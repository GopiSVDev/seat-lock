# API reference

Base URL locally: `http://localhost:8080`, the API gateway (the services themselves are not reachable from
outside). The API lives under **`/api/v1`** (`/api/v1/cities`,
`/api/v1/bookings/{id}` …), so a later `v2` can run beside it; webhooks, the internal endpoint, Swagger and
actuator sit outside it. All bodies are JSON (`Content-Type: application/json`). Times are
ISO-8601 in UTC (`2030-01-01T13:30:00Z`). Every `POST` that creates something returns `201 Created` with a
`Location` header pointing at the new resource. Money is an integer in **paise** (`15000` = ₹150), like
payment providers use, so there is no rounding; India only, so no currency field.

For the big picture (modules, booking flow, caching) see the [README](../README.md).

- [Authentication](#authentication)
- [Errors](#errors)
- [Catalogue](#catalogue): [cities](#cities), [movies](#movies), [theatres](#theatres), [screens](#screens), [shows](#shows)
- [Seat map](#seat-map): [layout](#get-apiv1showsidlayout), [availability](#get-apiv1showsidavailability)
- [Holds](#holds): [exact seats](#post-apiv1holds), [best available](#post-apiv1holdsbest-available), [get](#get-apiv1holdsid), [release](#delete-apiv1holdsid)
- [Booking](#booking): [check out](#post-apiv1bookings), [pay](#post-apiv1bookingsidpayment), [mine](#get-apiv1bookings), [get](#get-apiv1bookingsid), [cancel](#post-apiv1bookingsidcancel)
- [Internal](#internal-payment-service-only): [paid callback](#post-internalbookingsidpaid)
- [Enums](#enums)

## Authentication

Logins are handled by Keycloak (realm `seatlock`, locally at `http://localhost:8180`). Clients send the
access token as `Authorization: Bearer <token>`. The backend checks the token itself, without calling
Keycloak per request: signature (Keycloak's public keys, fetched once and cached), expiry, issuer
`http://localhost:8180/realms/seatlock` and audience `seatlock-api`. Roles come from the token's
`realm_access.roles` claim. Tokens live 5 minutes (1 hour for the dev-only client `seatlock-dev-cli`).

| Request | Who may call it |
|---|---|
| `GET` on `/api/v1/cities/**`, `/api/v1/movies/**`, `/api/v1/theatres/**`, `/api/v1/screens/**`, `/api/v1/shows/**` (includes the seat map) | anyone, no token |
| `POST` on `/api/v1/cities/**`, `/api/v1/movies/**`, `/api/v1/theatres/**` (includes screens), `/api/v1/shows/**` | `ADMIN` |
| `/api/v1/holds/**`, `/api/v1/bookings/**` | any logged-in user (`CUSTOMER` or `ADMIN`) |
| Swagger UI, `/v3/api-docs` | anyone |
| anything else | any logged-in user |

Test users: `admin`/`admin` (ADMIN and CUSTOMER), `asha`/`asha` and `ravi`/`ravi` (CUSTOMER). New sign-ups
get CUSTOMER.

Getting a token from the command line (client `seatlock-dev-cli`, password login, local development only):

```bash
curl -s localhost:8180/realms/seatlock/protocol/openid-connect/token \
  -d grant_type=password -d client_id=seatlock-dev-cli -d username=asha -d password=asha | jq -r .access_token
```

Apps and Swagger UI use client `seatlock-web` instead: the browser is sent to Keycloak's login page
(authorization code flow with PKCE), so the app never sees the password. In Swagger UI click
**Authorize**, then log in.

## Errors

Errors use [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457)
(`Content-Type: application/problem+json`):

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "One or more seats are already held or booked",
  "instance": "/holds"
}
```

| Status | When | `detail` |
|---|---|---|
| 400 | Request body fails validation; `errors` maps each bad field to its message | `Validation failed` |
| 400 | Malformed JSON or a wrong enum value | Spring's message |
| 400 | A query parameter out of range (e.g. `size=100`) | `Validation failure` |
| 400 | Filters that don't make sense together | see each endpoint |
| 401 | No token, or a token that is invalid or expired (also sends a `WWW-Authenticate: Bearer` header) | `Authentication required` or `Invalid or expired token` |
| 403 | Valid token, but the role may not call this endpoint (e.g. a CUSTOMER creating a movie) | `Not allowed for your role` |
| 404 | An id in the path or body does not exist (or is another user's hold or booking) | `<Resource> not found: <id>`, e.g. `Show not found: 42` |
| 409 | Seats already held or booked, no adjacent block left, a booking cancelled after its show started or before it was paid | see each endpoint |
| 409 | A unique rule broken, e.g. a duplicate screen name in a theatre, city or alias | `Request conflicts with existing data` |
| 429 | Too many hold requests from one user (also sends `Retry-After: <seconds>`) | `Too many hold requests, try again in 6 seconds` |
| 500 | Anything unexpected (details only in the server log) | `Unexpected error` |
| 502 | The payment service failed or could not be reached (details only in the server log) | e.g. `Payment service could not create payment` |
| 503 | Redis or the database can't be reached; nothing is held or booked | `Service temporarily unavailable` |
| 503 | Too many payments in progress, or the payment service is failing and calls are paused (also sends `Retry-After: <seconds>`) | `Payments are busy, try again in 5 seconds`, `Payments are unavailable, try again in 23 seconds` |

Validation example:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/holds/best-available",
  "errors": {
    "quantity": "must be less than or equal to 10",
    "seatType": "must match \"REGULAR|PREMIUM|RECLINER\""
  }
}
```

## Catalogue

Admin data: what is playing where. Owned by the `catalogue` module. Every `GET` is public; every `POST`
needs the `ADMIN` role.

### Cities

Cities are a fixed list (45 Indian cities and their aliases, loaded by a database migration). Theatres point
to a city by id, so a misspelt city can't be stored.

#### `GET /api/v1/cities`

Search or list cities. All parameters optional.

| Parameter | Meaning |
|---|---|
| `query` | Text the user typed, up to 50 characters. Matches city names **and aliases**, by prefix (`chen`) and by trigram similarity, so typos work (`chenai`, `banglore`). At most 10 results, best first: prefix matches, then real names before aliases, then closest. |
| `popular` | `true`: only the popular cities. Without `query` they come in popular order (Mumbai, Delhi, Bengaluru, ...). |
| `withTheatres` | `true`: only cities with at least one theatre. A customer's city picker sends this; an admin choosing a city for a new theatre doesn't. |

Without `query` the result is the whole (filtered) list, alphabetical unless `popular=true`.

`GET /api/v1/cities?query=madras` → `200`:

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

#### `GET /api/v1/cities/{id}`

One city, same shape (`matchedAlias` is `null`). `404` if unknown.

#### `POST /api/v1/cities`

```json
{
  "name": "Erode",
  "state": "Tamil Nadu",
  "popularRank": null
}
```

`name` and `state` required (max 100 characters, trimmed). `popularRank` optional, positive. Response `201`
with the city. `409` if the city already exists in that state (ignoring case) or the popular rank is taken.

#### `POST /api/v1/cities/{id}/aliases`

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

#### `POST /api/v1/movies`

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

#### `GET /api/v1/movies`

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

`GET /api/v1/movies?cityId=5&size=1` → `200`:

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

#### `GET /api/v1/movies/{id}`

Returns the same shape. `404` if unknown.

### Theatres

#### `POST /api/v1/theatres`

All fields required. `cityId` comes from [`GET /api/v1/cities`](#get-apiv1cities); `404` if unknown.

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

#### `GET /api/v1/theatres/{id}`

Returns the same shape. `404` if unknown.

#### `GET /api/v1/theatres`

A page of theatres, by name (ignoring case). The "Cinemas" tab sends `cityId`; an admin list doesn't.

| Parameter | Meaning |
|---|---|
| `cityId` | Optional. Only theatres in this city; `404` if the city is unknown. |
| `query` | Optional, up to 50 characters. Part of the name, ignoring case: `pvr` finds "PVR Grand" and "PVR Ampa Skywalk". |
| `page` | Page number from 0 (default 0). |
| `size` | Items per page, 1 to 50 (default 20). |

`GET /api/v1/theatres?cityId=5&query=pvr` → `200`:

```json
{
  "items": [
    {
      "id": 1,
      "name": "PVR Grand",
      "city": {
        "id": 5,
        "name": "Chennai"
      },
      "address": "1 Main Road"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

#### `GET /api/v1/theatres/{id}/shows`

A theatre's page: its shows on one **date in India**, grouped by movie. Only shows that haven't started yet.
The same data as [`GET /api/v1/shows`](#get-apiv1shows), seen from the theatre instead of the movie.

| Parameter | Meaning |
|---|---|
| `date` | Optional, `2026-11-05`. A day in India (00:00 to 24:00 IST); default today. A past date gives `400`. |
| `language` | Optional, repeatable, a [Language](#enums). |
| `format` | Optional, repeatable, a [ShowFormat](#enums). |

`GET /api/v1/theatres/1/shows?date=2026-12-02` → `200`:

```json
{
  "theatre": {
    "id": 1,
    "name": "PVR Grand",
    "city": {
      "id": 5,
      "name": "Chennai"
    },
    "address": "1 Main Road"
  },
  "date": "2026-12-02",
  "dates": ["2026-12-01", "2026-12-02", "2026-12-03"],
  "movies": [
    {
      "id": 5,
      "title": "Big Hit",
      "posterUrl": "https://cdn.example.com/posters/big-hit.jpg",
      "certification": "UA",
      "durationMinutes": 120,
      "shows": [
        {
          "id": 6,
          "startTime": "2026-12-02T10:00:00Z",
          "language": "ENGLISH",
          "format": "IMAX_2D",
          "screenName": "Screen 1"
        }
      ]
    }
  ]
}
```

Movies by title, shows by start time. `dates` is the date strip (next 7 days with upcoming shows matching the
filters). `404` if the theatre is unknown.

### Screens

#### `POST /api/v1/theatres/{theatreId}/screens`

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

#### `GET /api/v1/screens/{id}`

Returns the same shape. `404` if unknown.

### Shows

#### `POST /api/v1/shows`

Schedules a movie on a screen. Also creates one bookable show seat per seat of the screen, all AVAILABLE.

| Field | Type | Rules |
|---|---|---|
| `movieId` | long | required, must exist (else `404`) |
| `screenId` | long | required, must exist (else `404`) |
| `startTime` | instant | required, in the future |
| `language` | [Language](#enums) | required |
| `format` | [ShowFormat](#enums) | required |
| `prices` | map [SeatType](#enums) → long | paise, each > 0; exactly the seat types the screen has (else `400`) |

Prices are fixed once the show exists: show seats, holds and bookings copy them.

```json
{
  "movieId": 1,
  "screenId": 1,
  "startTime": "2030-01-01T13:30:00Z",
  "language": "ENGLISH",
  "format": "IMAX_2D",
  "prices": {
    "REGULAR": 15000,
    "RECLINER": 40000
  }
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
  "startTime": "2030-01-01T13:30:00Z",
  "endTime": "2030-01-01T16:19:00Z",
  "language": "ENGLISH",
  "format": "IMAX_2D",
  "prices": {
    "REGULAR": 15000,
    "RECLINER": 40000
  }
}
```

`GET /api/v1/shows/{id}` returns the same shape.

#### `GET /api/v1/shows`

The "pick a theatre and time" page: one movie's shows in one city on one **date in India**, grouped by
theatre. Only shows that haven't started yet. Not paged: one movie, one city, one day is naturally small.

| Parameter | Meaning |
|---|---|
| `movieId` | Required; `404` if unknown. |
| `cityId` | Required; `404` if unknown. |
| `date` | Optional, `2026-11-05`. A day in India (00:00 to 24:00 IST); default today. A past date gives `400`. |
| `language` | Optional, repeatable, a [Language](#enums). |
| `format` | Optional, repeatable, a [ShowFormat](#enums). |

`GET /api/v1/shows?movieId=5&cityId=5&date=2026-12-02` → `200`:

```json
{
  "movieId": 5,
  "cityId": 5,
  "date": "2026-12-02",
  "dates": ["2026-12-02", "2026-12-03", "2026-12-09"],
  "theatres": [
    {
      "id": 3,
      "name": "AGS Cinemas",
      "address": "Villivakkam",
      "shows": [
        {
          "id": 9,
          "startTime": "2026-12-02T18:29:00Z",
          "language": "TAMIL",
          "format": "TWO_D",
          "screenName": "Screen 3"
        }
      ]
    },
    {
      "id": 1,
      "name": "PVR Grand",
      "address": "1 Main Road",
      "shows": [
        {
          "id": 6,
          "startTime": "2026-12-02T10:00:00Z",
          "language": "ENGLISH",
          "format": "IMAX_2D",
          "screenName": "Screen 1"
        }
      ]
    }
  ]
}
```

- Theatres by name, shows by start time. Times are UTC like everywhere else: `18:29Z` is 23:59 IST, so that
  show belongs to 2 December; a show at `18:31Z` the same evening is 00:01 IST and belongs to 3 December.
- `dates`: the next days (up to 7) with at least one upcoming show matching the filters, for the date strip.
  It doesn't depend on `date`. An empty `theatres` list means nothing on that date.
- Seat availability per show is not included: the seat map's
  [`GET /api/v1/shows/{id}/availability`](#get-apiv1showsidavailability) has it.

#### `GET /api/v1/shows/{id}`

Returns the same shape. `404` if unknown.

## Seat map

What a customer sees before booking. Owned by the `inventory` module. Split in two so the part that never
changes can be cached everywhere and only the small part is fetched on every view: draw the layout, then
grey out the ids listed in `unavailable`.

#### `GET /api/v1/shows/{id}/layout`

The seats of a show in row order. The `id`s here are **show seat ids**, the ones you book. Each row has
one seat type, so one `price` (paise) for the price legend.

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
      "price": 15000,
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
      "price": 40000,
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

#### `GET /api/v1/shows/{id}/availability`

Free seat count and the ids of seats that are booked or held. May be up to 1 second stale (cached in the
app). Holding always re-checks, so staleness can cause a `409` but never a double sale.

```json
{
  "showId": 1,
  "availableSeats": 26,
  "unavailable": [1, 2]
}
```

`404` if the show is unknown.

## Holds

Owned by the `inventory` module. Buying is three steps, like on any ticket site: **hold** the seats
(nobody else can take them for 5 minutes), **check out** the hold with [`POST /api/v1/bookings`](#post-apiv1bookings)
(the hold is kept for the 10-minute payment window), then **pay**. A hold that isn't paid expires by itself
and its seats go back on sale; `DELETE` gives them back sooner (the back button). Holds live in Redis only:
the database learns about seats when they are paid for.

- All or nothing: if any seat is held or booked, nothing is held (`409`).
- One hold per user per show: a new hold replaces your previous one for that show (its seats are let go;
  seats in both stay yours).
- At most 10 seats per hold.
- Each user only sees their own holds; another user's hold answers `404`, like one that doesn't exist.
- Rate limited per user: `POST /api/v1/holds` and `POST /api/v1/holds/best-available` share a bucket of 10 requests that
  refills at 10 per minute (one every 6 s). Beyond that: `429` with a `Retry-After` header in seconds.
  A buyer changing seats a few times never hits it; a script holding and releasing in a loop does. Every
  request counts, including ones that fail validation or conflict. `GET`, `DELETE`, checkout and payment
  are not limited. The buckets live in Redis, so the limit holds across backend instances; if Redis is down
  the limit is skipped (holds then fail with `503` anyway).

#### `POST /api/v1/holds`

Holds exactly the seats the customer picked.

| Field | Type | Rules |
|---|---|---|
| `showId` | long | required |
| `showSeatIds` | long[] | 1 to 10; show seat ids from the layout |

```json
{
  "showId": 1,
  "showSeatIds": [1, 2]
}
```

Response `201` with `Location: /holds/{id}`:

```json
{
  "id": "3f6c1e9a-7b2d-4c1e-9f0a-5d8e2b7c4a11",
  "showId": 1,
  "expiresAt": "2026-11-05T10:20:30.123Z",
  "totalAmount": 30000,
  "seats": [
    {
      "showSeatId": 1,
      "label": "A1",
      "price": 15000
    },
    {
      "showSeatId": 2,
      "label": "A2",
      "price": 15000
    }
  ]
}
```

`expiresAt` drives the seat-selection timer (checkout replaces it with the booking's `paymentExpiresAt`). `totalAmount` is the sum of the seat prices (paise), what checkout
will charge.

| Error | When |
|---|---|
| `409 One or more seats are already held or booked` | any seat is in someone else's hold or booked |
| `404 Seats for show not found: <showId>` | a seat id does not exist or belongs to another show |
| `429 Too many hold requests, try again in N seconds` | more than the user's [hold rate limit](#holds) |

#### `POST /api/v1/holds/best-available`

The server picks the best block of `quantity` adjacent seats (same row, consecutive numbers) of the
requested type, by seat rank, skipping booked and held seats, and holds it. If another buyer holds a
seat of that block first, it picks again (up to 3 times).

| Field | Type | Rules |
|---|---|---|
| `showId` | long | required |
| `quantity` | int | 1 to 10 |
| `seatType` | [SeatType](#enums) | required |

```json
{
  "showId": 1,
  "quantity": 2,
  "seatType": "RECLINER"
}
```

Response `201`: same shape as `POST /api/v1/holds`.

| Error | When |
|---|---|
| `409 No 2 adjacent RECLINER seats available` | no free block of that size and type (free seats may still exist, but not side by side) |
| `404 Show not found: <showId>` | unknown show |
| `429 Too many hold requests, try again in N seconds` | shares the bucket with `POST /api/v1/holds` |

#### `GET /api/v1/holds/{id}`

The caller's hold, same shape as `POST /api/v1/holds`. For a checkout page after a refresh. `404 Hold not found:
<id>` once it has expired, been paid for or released, or if it is someone else's.

#### `DELETE /api/v1/holds/{id}`

Lets the seats go now. `204`, or `404 Hold not found: <id>` (as for `GET`).

## Booking

Owned by the `booking` module. A booking is made from a [hold](#holds) and paid with **Razorpay**: the
seats are booked only once the payment is verified. All booking endpoints need a logged-in user, and each
user only ever sees their own bookings: the owner is the token's `sub` (the user's permanent Keycloak id),
never a field the client sends. Another user's booking answers `404`, exactly like a booking that doesn't
exist, so booking ids can't be probed. `customerName` in responses is the user's name from the token at
booking time.

Every booking also keeps a copy of its show (`show`: time, language, format, movie, theatre, screen) taken
when it was booked, the way an order keeps the product name. A booking page renders from it alone, without
calling the catalogue endpoints, and still shows what was sold if catalogue data changes later.

Each seat keeps its `price` at booking time and the booking keeps `totalAmount` (paise), like order lines.
A cancelled booking keeps its amounts.

**Statuses:**

| Status | Meaning | Seats | In `GET /api/v1/bookings` |
|---|---|---|---|
| `PENDING_PAYMENT` | checked out, waiting for payment until `paymentExpiresAt` | held (Redis), not booked | no |
| `CONFIRMED` | paid; the seats are yours | booked | yes |
| `EXPIRED` | not paid in time (a job marks it within 30 s of the deadline) | free again | no |
| `FAILED` | paid, but the hold was lost before the seats could be booked (e.g. a new hold for the same show replaced it); the payment is refunded automatically | free | yes |
| `CANCELLED` | cancelled by the customer; the payment is refunded | free again | yes |

**The flow in a browser:**

1. `POST /api/v1/bookings {holdId}` → `PENDING_PAYMENT` booking with a `checkout` object.
2. Open [Razorpay Checkout](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/)
   with `key = checkout.keyId`, `order_id = checkout.razorpayOrderId`, `amount`, `currency`, and a `timeout`
   that ends before `paymentExpiresAt`. A declined card doesn't end the order: the buyer can try again.
3. Checkout's success handler gets `razorpay_order_id`, `razorpay_payment_id` and `razorpay_signature`;
   send them to `POST /api/v1/bookings/{id}/payment` → `CONFIRMED`.

If the browser never gets to step 3 (tab closed, network lost), Razorpay's webhook confirms the booking
anyway, through the payment service and [`POST /internal/bookings/{id}/paid`](#post-internalbookingsidpaid).
`/dev/checkout.html` on the backend is a dev-only page that does all three steps (paste a token and a hold
id).

#### `POST /api/v1/bookings`

Checkout: turns one of your holds into an unpaid booking and starts its payment. The hold is kept alive
for the 10-minute payment window (`paymentExpiresAt`); the seats stay held, not booked.

| Field | Type | Rules |
|---|---|---|
| `holdId` | string | required; from `POST /api/v1/holds` |

```json
{
  "holdId": "3f6c1e9a-7b2d-4c1e-9f0a-5d8e2b7c4a11"
}
```

Response `201` with `Location: /bookings/{id}`:

```json
{
  "id": 1,
  "customerName": "Asha Kumar",
  "status": "PENDING_PAYMENT",
  "createdAt": "2026-11-03T10:15:30.123456Z",
  "cancelledAt": null,
  "totalAmount": 30000,
  "paymentExpiresAt": "2026-11-03T10:25:30.123Z",
  "checkout": {
    "razorpayOrderId": "order_Nx2kZq8V1aBcDe",
    "keyId": "rzp_test_1aB2c3D4e5F6g7",
    "amount": 30000,
    "currency": "INR"
  },
  "show": {
    "id": 1,
    "startTime": "2030-01-01T13:30:00Z",
    "language": "ENGLISH",
    "format": "IMAX_2D",
    "movie": {
      "id": 1,
      "title": "Interstellar",
      "posterUrl": "https://cdn.example.com/posters/interstellar.jpg",
      "certification": "UA"
    },
    "theatre": {
      "id": 1,
      "name": "PVR Grand"
    },
    "screenName": "Screen 1"
  },
  "seats": [
    {
      "showSeatId": 1,
      "label": "A1",
      "price": 15000
    },
    {
      "showSeatId": 2,
      "label": "A2",
      "price": 15000
    }
  ]
}
```

One booking per hold: calling it again with the same `holdId` (a double tap, a page refresh, a retry after
a `502`) returns that booking with `200` and the same Razorpay order; the payment window is not extended
again. `keyId` is Razorpay's public key id; the secret never leaves the payment service. `checkout` appears
only in this response; `paymentExpiresAt` is set while the booking is `PENDING_PAYMENT`.

| Error | When |
|---|---|
| `404 Hold not found: <holdId>` | the hold expired, was released, or is someone else's |
| `502 Payment service could not create payment` | the payment service or Razorpay failed; the booking exists, retry the same request |
| `503 Payments are busy, ...` / `503 Payments are unavailable, ...` | too many payments at once, or the payment service is failing; the booking may exist, retry the same request after `Retry-After` |

#### `POST /api/v1/bookings/{id}/payment`

What the browser sends after Razorpay Checkout succeeded. The payment service checks the signature (an
HMAC of `order_id|payment_id` with the Razorpay key secret), then the seats are booked. Safe to repeat.

| Field | Type | Rules |
|---|---|---|
| `razorpayOrderId` | string | required, from Checkout |
| `razorpayPaymentId` | string | required, from Checkout |
| `razorpaySignature` | string | required, from Checkout |

```json
{
  "razorpayOrderId": "order_Nx2kZq8V1aBcDe",
  "razorpayPaymentId": "pay_Nx2lA9cQ3dEfGh",
  "razorpaySignature": "9f1c...e4"
}
```

Response `200`: the booking (same shape as `GET /api/v1/bookings/{id}`), usually `CONFIRMED`. It is `FAILED` if
the hold was lost before the payment arrived; the money is then refunded automatically.

| Error | When |
|---|---|
| `400 Invalid payment signature` | the three values don't match (forged, or from another order) |
| `404 Booking not found: <id>` | unknown, or another user's booking |
| `409 Payment has not been started` | checkout never reached the payment service (retry `POST /api/v1/bookings`) |
| `502` | the payment service failed or could not be reached |
| `503` | too many payments at once, or the payment service is failing; retry after `Retry-After` |

#### `GET /api/v1/bookings`

The caller's orders, as two tabs: `CONFIRMED`, `CANCELLED` and `FAILED` bookings (unpaid ones aren't
orders yet). Paged like the other lists.

| Query parameter | Rules |
|---|---|
| `period` | `UPCOMING` (default): shows that haven't started, soonest first. `PAST`: shows that have started, latest first |
| `page` | from 0, default 0 |
| `size` | 1 to 50, default 20 |

Response `200`:

```json
{
  "items": [
    {
      "id": 1,
      "customerName": "Asha Kumar",
      "status": "CONFIRMED",
      "createdAt": "2026-11-03T10:15:30.123456Z",
      "cancelledAt": null,
      "totalAmount": 30000,
      "show": {
        "id": 1,
        "startTime": "2030-01-01T13:30:00Z",
        "language": "ENGLISH",
        "format": "IMAX_2D",
        "movie": {
          "id": 1,
          "title": "Interstellar",
          "posterUrl": "https://cdn.example.com/posters/interstellar.jpg",
          "certification": "UA"
        },
        "theatre": {
          "id": 1,
          "name": "PVR Grand"
        },
        "screenName": "Screen 1"
      },
      "seats": [
        {
          "showSeatId": 1,
          "label": "A1",
          "price": 15000
        },
        {
          "showSeatId": 2,
          "label": "A2",
          "price": 15000
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

#### `GET /api/v1/bookings/{id}`

Returns the same shape as the create response. `404` if unknown or owned by another user.

#### `POST /api/v1/bookings/{id}/cancel`

Cancels a confirmed booking before its show starts: the booking becomes `CANCELLED` (it stays in the
caller's list, with `cancelledAt` set), its seats go back on sale and the full amount is refunded through
Razorpay (normal speed: days, not seconds). The seat map shows the seats free within about a second
(availability is cached for 1 s). No body.

Idempotent: cancelling an already cancelled booking returns it unchanged with `200`, so a client can safely
retry after a timeout. Two cancels arriving at once release the seats only once.

Response `200`: the booking, same shape as `GET /api/v1/bookings/{id}`, with `"status": "CANCELLED"` and
`"cancelledAt": "2026-11-04T09:00:00.123456Z"`.

| Error | When |
|---|---|
| `404 Booking not found: <id>` | unknown, or another user's booking |
| `409 Show has already started` | the show's start time has passed |
| `409 Only confirmed bookings can be cancelled` | the booking is unpaid, expired or failed |

## Internal (payment service only)

Not for browsers, and not reachable through the gateway (`404` from outside). Inside Docker, callers authenticate with a Keycloak **client credentials** token of the payment
service's own client (`seatlock-payment`), which carries the realm role `BOOKING_CALLBACK`; any other token
gets `403`.

#### `POST /internal/bookings/{id}/paid`

The payment service calls this when Razorpay's `payment.captured` webhook arrives. It confirms the booking
exactly like [`POST /api/v1/bookings/{id}/payment`](#post-apiv1bookingsidpayment) (the second of the two changes
nothing), so a buyer who paid and closed the tab still gets the seats.

```json
{
  "paymentId": 7
}
```

Response `204`. `404` if the booking is unknown, `409 Booking has another payment` if the id doesn't match.
Any failure makes the payment service answer the webhook with an error, so Razorpay delivers it again.

## Enums

| Enum | Values |
|---|---|
| Certification | `U`, `UA`, `A` |
| Language | `ENGLISH`, `HINDI`, `TAMIL`, `TELUGU`, `MALAYALAM`, `KANNADA`, `BENGALI`, `MARATHI`, `GUJARATI`, `PUNJABI`, `ODIA`, `ASSAMESE`, `BHOJPURI`, `URDU`, `JAPANESE`, `KOREAN`, `FRENCH`, `SPANISH` |
| Genre | `ACTION`, `ADVENTURE`, `ANIMATION`, `BIOGRAPHY`, `COMEDY`, `CRIME`, `DOCUMENTARY`, `DRAMA`, `FAMILY`, `FANTASY`, `HISTORICAL`, `HORROR`, `MUSICAL`, `MYSTERY`, `ROMANCE`, `SCI_FI`, `SPORTS`, `THRILLER`, `WAR` |
| SeatType | `REGULAR`, `PREMIUM`, `RECLINER` |
| ShowFormat | `TWO_D`, `THREE_D`, `IMAX_2D`, `IMAX_3D`, `FOUR_DX` |
| Booking status | `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `FAILED`, `CANCELLED` |

## Operations

Inside Docker only: the gateway does not route `/actuator/**` (`404` from outside).

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | liveness/readiness |
| `GET /actuator/prometheus` | metrics (request latency histograms, connection pool, caches, JVM) |
