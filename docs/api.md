# API reference

Base URL locally: `http://localhost:8080`. All bodies are JSON (`Content-Type: application/json`). Times are
ISO-8601 in UTC (`2030-01-01T18:30:00Z`). Every `POST` that creates something returns `201 Created` with a
`Location` header pointing at the new resource.

For the big picture (modules, booking flow, caching) see the [README](../README.md).

- [Errors](#errors)
- [Catalogue](#catalogue): [cities](#cities), [movies](#movies), [theatres](#theatres), [screens](#screens), [shows](#shows)
- [Seat map](#seat-map): [layout](#get-showsidlayout), [availability](#get-showsidavailability)
- [Booking](#booking): [exact seats](#post-bookings), [best available](#post-bookingsbest-available), [get](#get-bookingsid)
- [Enums](#enums)

## Errors

Errors use [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457)
(`Content-Type: application/problem+json`):

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "One or more seats are already booked",
  "instance": "/bookings"
}
```

| Status | When | `detail` |
|---|---|---|
| 400 | Request body fails validation; `errors` maps each bad field to its message | `Validation failed` |
| 400 | Malformed JSON or a wrong enum value | Spring's message |
| 400 | A query parameter out of range (e.g. `size=100`) | `Validation failure` |
| 400 | Filters that don't make sense together | see each endpoint |
| 404 | An id in the path or body does not exist | `<Resource> not found: <id>`, e.g. `Show not found: 42` |
| 409 | Seats already taken or no adjacent block left | see each endpoint |
| 409 | A unique rule broken, e.g. a duplicate screen name in a theatre, city or alias | `Request conflicts with existing data` |
| 500 | Anything unexpected (details only in the server log) | `Unexpected error` |

Validation example:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/bookings/best-available",
  "errors": {
    "quantity": "must be less than or equal to 10",
    "seatType": "must match \"REGULAR|PREMIUM|RECLINER\""
  }
}
```

## Catalogue

Admin data: what is playing where. Owned by the `catalogue` module.

### Cities

Cities are a fixed list (45 Indian cities and their aliases, loaded by a database migration). Theatres point
to a city by id, so a misspelt city can't be stored.

#### `GET /cities`

Search or list cities. All parameters optional.

| Parameter | Meaning |
|---|---|
| `query` | Text the user typed, up to 50 characters. Matches city names **and aliases**, by prefix (`chen`) and by trigram similarity, so typos work (`chenai`, `banglore`). At most 10 results, best first: prefix matches, then real names before aliases, then closest. |
| `popular` | `true`: only the popular cities. Without `query` they come in popular order (Mumbai, Delhi, Bengaluru, ...). |
| `withTheatres` | `true`: only cities with at least one theatre. A customer's city picker sends this; an admin choosing a city for a new theatre doesn't. |

Without `query` the result is the whole (filtered) list, alphabetical unless `popular=true`.

`GET /cities?query=madras` → `200`:

```json
[
  {
    "id": 5,
    "name": "Chennai",
    "state": "Tamil Nadu",
    "popularRank": 5,
    "matchedAlias": "Madras"
  }
]
```

`matchedAlias` is set when the city was found through an alias, so a UI can show "Chennai (Madras)"; it is
`null` when the name matched or no `query` was sent. `popularRank` is `null` for cities outside the popular
list. No match gives `[]`; a `query` over 50 characters gives `400`.

#### `GET /cities/{id}`

One city, same shape (`matchedAlias` is `null`). `404` if unknown.

#### `POST /cities`

```json
{
  "name": "Erode",
  "state": "Tamil Nadu",
  "popularRank": null
}
```

`name` and `state` required (max 100 characters, trimmed). `popularRank` optional, positive. Response `201`
with the city. `409` if the city already exists in that state (ignoring case) or the popular rank is taken.

#### `POST /cities/{id}/aliases`

Another name people type for the city: old name, short name or common spelling.

```json
{
  "alias": "Tiruchi"
}
```

Response `201`:

```json
{
  "id": 26,
  "cityId": 23,
  "alias": "Tiruchi"
}
```

`404` if the city is unknown, `409` if any city already has that alias (ignoring case).

### Movies

#### `POST /movies`

| Field | Type | Rules |
|---|---|---|
| `title` | string | required |
| `description` | string | optional |
| `durationMinutes` | int | > 0; used to compute a show's end time |
| `certification` | [Certification](#enums) | required |
| `releaseDate` | date (`2014-11-07`) | required |
| `posterUrl` | string | optional; a valid URL, max 500 characters. A link to the image on a CDN or file storage, never the image itself |
| `genres` | [Genre](#enums)[] | required, 1 to 3; duplicates are stored once |

```json
{
  "title": "Interstellar",
  "description": "Space epic",
  "durationMinutes": 169,
  "certification": "UA",
  "releaseDate": "2014-11-07",
  "posterUrl": "https://cdn.example.com/posters/interstellar.jpg",
  "genres": ["SCI_FI", "DRAMA"]
}
```

Response `201`:

```json
{
  "id": 1,
  "title": "Interstellar",
  "description": "Space epic",
  "durationMinutes": 169,
  "certification": "UA",
  "releaseDate": "2014-11-07",
  "posterUrl": "https://cdn.example.com/posters/interstellar.jpg",
  "genres": ["SCI_FI", "DRAMA"]
}
```

`posterUrl` is `null` when the movie has no poster yet.

#### `GET /movies`

A page of movie cards. With `cityId`: **now showing** in that city, i.e. movies with at least one show that
hasn't started yet in one of its theatres, most upcoming shows first (theatres give the most shows to the
films people want). Without `cityId`: the whole catalogue, newest release first (for admins scheduling shows).

| Parameter | Meaning |
|---|---|
| `cityId` | Optional. Now showing in this city; `404` if the city is unknown. |
| `language` | Optional, repeatable, a [Language](#enums). Has an upcoming show in this language in the city. Needs `cityId`. |
| `format` | Optional, repeatable, a [ShowFormat](#enums). Has an upcoming show in this format in the city. Needs `cityId`. |
| `genre` | Optional, repeatable, a [Genre](#enums). The movie has this genre. |
| `page` | Page number from 0 (default 0). |
| `size` | Items per page, 1 to 50 (default 20). |

Several values of one filter mean **or**, different filters mean **and**:
`?cityId=5&language=TAMIL&language=ENGLISH&genre=THRILLER` = thrillers with a Tamil or English show in
Chennai. `language` or `format` without `cityId` gives `400`.

`GET /movies?cityId=5&size=1` → `200`:

```json
{
  "items": [
    {
      "id": 5,
      "title": "Big Hit",
      "posterUrl": "https://cdn.example.com/posters/big-hit.jpg",
      "genres": ["ACTION", "THRILLER"],
      "certification": "UA",
      "durationMinutes": 120,
      "releaseDate": "2026-10-01",
      "languages": ["ENGLISH", "TAMIL"],
      "formats": ["IMAX_2D", "TWO_D"]
    }
  ],
  "page": 0,
  "size": 1,
  "totalItems": 3,
  "totalPages": 3
}
```

`languages` and `formats` list everything the movie's upcoming shows offer **in that city**, whatever the
filters; they are `null` without `cityId`. A page past the end returns empty `items` with the real totals.

#### `GET /movies/{id}`

Returns the same shape. `404` if unknown.

### Theatres

#### `POST /theatres`

All fields required. `cityId` comes from [`GET /cities`](#get-cities); `404` if unknown.

```json
{
  "name": "PVR Grand",
  "cityId": 5,
  "address": "1 Main Road"
}
```

Response `201`:

```json
{
  "id": 1,
  "name": "PVR Grand",
  "city": {
    "id": 5,
    "name": "Chennai"
  },
  "address": "1 Main Road"
}
```

#### `GET /theatres/{id}`

Returns the same shape. `404` if unknown.

### Screens

#### `POST /theatres/{theatreId}/screens`

A screen is its seat layout. Rows are listed front to back; each row has one seat type and seats numbered
1..`seatCount` from the left. Every seat gets a best-available **rank** (1 = best) when the screen is
created; see the README.

| Field | Type | Rules |
|---|---|---|
| `name` | string | required, unique within the theatre (else `409`) |
| `rows` | array | at least one |
| `rows[].label` | string | required, e.g. `"A"` |
| `rows[].seatCount` | int | > 0 |
| `rows[].seatType` | [SeatType](#enums) | required |

```json
{
  "name": "Screen 1",
  "rows": [
    {
      "label": "A",
      "seatCount": 10,
      "seatType": "REGULAR"
    },
    {
      "label": "B",
      "seatCount": 10,
      "seatType": "REGULAR"
    },
    {
      "label": "C",
      "seatCount": 8,
      "seatType": "RECLINER"
    }
  ]
}
```

Response `201` (seats abbreviated):

```json
{
  "id": 1,
  "theatreId": 1,
  "name": "Screen 1",
  "totalSeats": 28,
  "rows": [
    {
      "label": "A",
      "seats": [
        {
          "id": 1,
          "number": 1,
          "seatType": "REGULAR"
        },
        {
          "id": 2,
          "number": 2,
          "seatType": "REGULAR"
        }
      ]
    },
    {
      "label": "C",
      "seats": [
        {
          "id": 21,
          "number": 1,
          "seatType": "RECLINER"
        }
      ]
    }
  ]
}
```

`404` if the theatre is unknown. These are physical seat ids; bookings use show seat ids (see seat map).

#### `GET /screens/{id}`

Returns the same shape. `404` if unknown.

### Shows

#### `POST /shows`

Schedules a movie on a screen. Also creates one bookable show seat per seat of the screen, all AVAILABLE.

| Field | Type | Rules |
|---|---|---|
| `movieId` | long | required, must exist (else `404`) |
| `screenId` | long | required, must exist (else `404`) |
| `startTime` | instant | required, in the future |
| `language` | [Language](#enums) | required |
| `format` | [ShowFormat](#enums) | required |

```json
{
  "movieId": 1,
  "screenId": 1,
  "startTime": "2030-01-01T18:30:00Z",
  "language": "ENGLISH",
  "format": "IMAX_2D"
}
```

Response `201` (`endTime` = start + movie duration):

```json
{
  "id": 1,
  "movieId": 1,
  "movieTitle": "Interstellar",
  "screenId": 1,
  "screenName": "Screen 1",
  "startTime": "2030-01-01T18:30:00Z",
  "endTime": "2030-01-01T21:19:00Z",
  "language": "ENGLISH",
  "format": "IMAX_2D"
}
```

#### `GET /shows/{id}`

Returns the same shape. `404` if unknown.

## Seat map

What a customer sees before booking. Owned by the `inventory` module. Split in two so the part that never
changes can be cached everywhere and only the small part is fetched on every view: draw the layout, then
grey out the ids listed in `unavailable`.

#### `GET /shows/{id}/layout`

The seats of a show in row order. The `id`s here are **show seat ids**, the ones you book.

Headers: `Cache-Control: max-age=86400, public, immutable`. Safe for browsers and CDNs to keep for a day,
since a show's layout never changes.

```json
{
  "showId": 1,
  "totalSeats": 28,
  "rows": [
    {
      "label": "A",
      "seatType": "REGULAR",
      "seats": [
        {
          "id": 1,
          "number": 1
        },
        {
          "id": 2,
          "number": 2
        }
      ]
    },
    {
      "label": "C",
      "seatType": "RECLINER",
      "seats": [
        {
          "id": 21,
          "number": 1
        }
      ]
    }
  ]
}
```

`404` if the show is unknown.

#### `GET /shows/{id}/availability`

Free seat count and the ids of seats that are not available. May be up to 1 second stale (cached in the
app). Booking always re-checks in the database, so staleness can cause a `409` but never a double sale.

```json
{
  "showId": 1,
  "availableSeats": 26,
  "unavailable": [1, 2]
}
```

`404` if the show is unknown.

## Booking

Owned by the `booking` module. A booking is confirmed immediately (no hold or payment step yet).

#### `POST /bookings`

Books exactly the seats the customer picked. All or nothing: if any seat is taken, nothing is booked.

| Field | Type | Rules |
|---|---|---|
| `showId` | long | required |
| `customerName` | string | required |
| `showSeatIds` | long[] | at least one; show seat ids from the layout |

```json
{
  "showId": 1,
  "customerName": "asha",
  "showSeatIds": [1, 2]
}
```

Response `201`:

```json
{
  "id": 1,
  "showId": 1,
  "customerName": "asha",
  "status": "CONFIRMED",
  "createdAt": "2026-11-03T10:15:30.123456Z",
  "seats": [
    {
      "showSeatId": 1,
      "label": "A1"
    },
    {
      "showSeatId": 2,
      "label": "A2"
    }
  ]
}
```

| Error | When |
|---|---|
| `409 One or more seats are already booked` | any requested seat is not AVAILABLE |
| `404 Seats for show not found: <showId>` | a seat id does not exist or belongs to another show |

#### `POST /bookings/best-available`

The server picks the best block of `quantity` adjacent seats (same row, consecutive numbers) of the
requested type, by seat rank. Concurrent requests skip seats another request is booking at that moment,
so they get different seats instead of colliding.

| Field | Type | Rules |
|---|---|---|
| `showId` | long | required |
| `customerName` | string | required |
| `quantity` | int | 1 to 10 |
| `seatType` | [SeatType](#enums) | required |

```json
{
  "showId": 1,
  "customerName": "ravi",
  "quantity": 2,
  "seatType": "RECLINER"
}
```

Response `201`: same shape as `POST /bookings`.

| Error | When |
|---|---|
| `409 No 2 adjacent RECLINER seats available` | no block of that size and type is free (free seats may still exist, but not side by side) |
| `404 Show not found: <showId>` | unknown show |

#### `GET /bookings/{id}`

Returns the same shape as the create response. `404` if unknown.

## Enums

| Enum | Values |
|---|---|
| Certification | `U`, `UA`, `A` |
| Language | `ENGLISH`, `HINDI`, `TAMIL`, `TELUGU`, `MALAYALAM`, `KANNADA`, `BENGALI`, `MARATHI`, `GUJARATI`, `PUNJABI`, `ODIA`, `ASSAMESE`, `BHOJPURI`, `URDU`, `JAPANESE`, `KOREAN`, `FRENCH`, `SPANISH` |
| Genre | `ACTION`, `ADVENTURE`, `ANIMATION`, `BIOGRAPHY`, `COMEDY`, `CRIME`, `DOCUMENTARY`, `DRAMA`, `FAMILY`, `FANTASY`, `HISTORICAL`, `HORROR`, `MUSICAL`, `MYSTERY`, `ROMANCE`, `SCI_FI`, `SPORTS`, `THRILLER`, `WAR` |
| SeatType | `REGULAR`, `PREMIUM`, `RECLINER` |
| ShowFormat | `TWO_D`, `THREE_D`, `IMAX_2D`, `IMAX_3D`, `FOUR_DX` |
| Booking status | `CONFIRMED` |

## Operations

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | liveness/readiness |
| `GET /actuator/prometheus` | metrics (request latency histograms, connection pool, caches, JVM) |
