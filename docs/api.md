# API reference

Base URL locally: `http://localhost:8080`. All bodies are JSON (`Content-Type: application/json`). Times are
ISO-8601 in UTC (`2030-01-01T18:30:00Z`). Every `POST` that creates something returns `201 Created` with a
`Location` header pointing at the new resource.

For the big picture (modules, booking flow, caching) see the [README](../README.md).

- [Errors](#errors)
- [Catalogue](#catalogue): [movies](#movies), [theatres](#theatres), [screens](#screens), [shows](#shows)
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
| 404 | An id in the path or body does not exist | `<Resource> not found: <id>`, e.g. `Show not found: 42` |
| 409 | Seats already taken or no adjacent block left | see each endpoint |
| 409 | A unique rule broken, e.g. a duplicate screen name in a theatre | `Request conflicts with existing data` |
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

### Movies

#### `POST /movies`

| Field | Type | Rules |
|---|---|---|
| `title` | string | required |
| `description` | string | optional |
| `durationMinutes` | int | > 0; used to compute a show's end time |
| `certification` | [Certification](#enums) | required |
| `releaseDate` | date (`2014-11-07`) | required |

```json
{"title": "Interstellar", "description": "Space epic", "durationMinutes": 169,
 "certification": "UA", "releaseDate": "2014-11-07"}
```

Response `201`:

```json
{"id": 1, "title": "Interstellar", "description": "Space epic", "durationMinutes": 169,
 "certification": "UA", "releaseDate": "2014-11-07"}
```

#### `GET /movies/{id}`

Returns the same shape. `404` if unknown.

### Theatres

#### `POST /theatres`

All fields required.

```json
{"name": "PVR Grand", "city": "Chennai", "address": "1 Main Road"}
```

Response `201`: `{"id": 1, "name": "PVR Grand", "city": "Chennai", "address": "1 Main Road"}`

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
{"name": "Screen 1", "rows": [
  {"label": "A", "seatCount": 10, "seatType": "REGULAR"},
  {"label": "B", "seatCount": 10, "seatType": "REGULAR"},
  {"label": "C", "seatCount": 8, "seatType": "RECLINER"}
]}
```

Response `201` (seats abbreviated):

```json
{"id": 1, "theatreId": 1, "name": "Screen 1", "totalSeats": 28, "rows": [
  {"label": "A", "seats": [{"id": 1, "number": 1, "seatType": "REGULAR"}, {"id": 2, "number": 2, "seatType": "REGULAR"}]},
  {"label": "C", "seats": [{"id": 21, "number": 1, "seatType": "RECLINER"}]}
]}
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
| `language` | string | required, e.g. `"English"` |
| `format` | [ShowFormat](#enums) | required |

```json
{"movieId": 1, "screenId": 1, "startTime": "2030-01-01T18:30:00Z", "language": "English", "format": "IMAX_2D"}
```

Response `201` (`endTime` = start + movie duration):

```json
{"id": 1, "movieId": 1, "movieTitle": "Interstellar", "screenId": 1, "screenName": "Screen 1",
 "startTime": "2030-01-01T18:30:00Z", "endTime": "2030-01-01T21:19:00Z",
 "language": "English", "format": "IMAX_2D"}
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
{"showId": 1, "totalSeats": 28, "rows": [
  {"label": "A", "seatType": "REGULAR", "seats": [{"id": 1, "number": 1}, {"id": 2, "number": 2}]},
  {"label": "C", "seatType": "RECLINER", "seats": [{"id": 21, "number": 1}]}
]}
```

`404` if the show is unknown.

#### `GET /shows/{id}/availability`

Free seat count and the ids of seats that are not available. May be up to 1 second stale (cached in the
app). Booking always re-checks in the database, so staleness can cause a `409` but never a double sale.

```json
{"showId": 1, "availableSeats": 26, "unavailable": [1, 2]}
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
{"showId": 1, "customerName": "asha", "showSeatIds": [1, 2]}
```

Response `201`:

```json
{"id": 1, "showId": 1, "customerName": "asha", "status": "CONFIRMED",
 "createdAt": "2026-11-03T10:15:30.123456Z",
 "seats": [{"showSeatId": 1, "label": "A1"}, {"showSeatId": 2, "label": "A2"}]}
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
{"showId": 1, "customerName": "ravi", "quantity": 2, "seatType": "RECLINER"}
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
| SeatType | `REGULAR`, `PREMIUM`, `RECLINER` |
| ShowFormat | `TWO_D`, `THREE_D`, `IMAX_2D`, `IMAX_3D`, `FOUR_DX` |
| Booking status | `CONFIRMED` |

## Operations

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | liveness/readiness |
| `GET /actuator/prometheus` | metrics (request latency histograms, connection pool, caches, JVM) |
