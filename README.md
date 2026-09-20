# Athlefit Backend

REST backend for the Athlefit React Native sports-venue booking app. It replaces direct Cloud Firestore access while retaining Firebase Authentication as the identity provider.

## Stack

- Java 17
- Spring Boot 4.1
- Spring Web MVC, Security, Validation, Data JPA
- PostgreSQL 17
- Flyway migrations
- Firebase ID-token verification through Spring Security's JWT resource server
- Geoapify for venue discovery and onboarding, with no mobile SDK dependency
- OpenAPI/Swagger UI

## Architecture

```text
React Native
  |-- Firebase email/password login
  |-- Authorization: Bearer <Firebase ID token>
          |
Spring Boot API
  |-- verifies Firebase issuer, audience, signature, and expiry
  |-- enforces profile ownership and booking rules
  |-- imports venue details/hours from Geoapify during admin onboarding
          |
PostgreSQL
  |-- users, venues, sports, prices, courts, bookings
  |-- database constraint prevents overlapping court bookings
```

No Firebase service-account JSON is required for token verification. The backend reads Google's public signing keys and validates that tokens belong to Firebase project `athlete-376013` by default.

## Project Structure

The code uses a conventional layered Spring structure so responsibilities are easy to find:

```text
src/main/java/com/athlefit/backend/
├── config/       application and security configuration
├── controller/   REST endpoints
├── dto/
│   ├── request/  validated client input
│   └── response/ stable API output
├── exception/    API error handling
├── model/        JPA entities and enums
├── repository/   database access
├── security/     authenticated Firebase identity
└── service/      business rules and transactions
```

Controllers only translate HTTP requests, services own business rules, repositories own persistence queries, and Flyway migrations own the database schema. The project deliberately avoids one-implementation service interfaces, field injection, and unnecessary mapping frameworks.

## Run Locally

Requirements: Java 17 and Docker Desktop.

```bash
cd "/Users/eki/React Native/Backend/Althefit-macro"
docker compose up -d
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` and applies Flyway migrations automatically. Swagger UI is at `http://localhost:8080/swagger-ui.html`.

Stop only the database container with:

```bash
docker compose stop
```

Use `docker compose down` to remove the container while keeping the named database volume. Do not add `-v` unless deleting all local Athlefit database data is intentional.

## Configuration

Defaults are suitable for the included Compose database. Override these environment variables when needed:

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/athlefit` |
| `DATABASE_USERNAME` | `athlefit` |
| `DATABASE_PASSWORD` | `athlefit` |
| `FIREBASE_PROJECT_ID` | `athlete-376013` |
| `ADMIN_FIREBASE_UIDS` | empty; comma-separated Firebase UIDs |
| `GEOAPIFY_API_KEY` | empty; server-side Geoapify API key |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*` |
| `PORT` | `8080` |

`.env.example` documents the values, but Spring Boot does not automatically load `.env`; export variables through the shell, IDE, deployment platform, or container configuration. Never commit production credentials or Firebase service accounts.

## API

Public endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/sports` | List sports |
| GET | `/api/v1/venues` | List venues |
| GET | `/api/v1/venues?category=badminton` | Filter by sport slug |
| GET | `/api/v1/venues/{id}[?sport=slug]` | Venue detail with every offered sport |
| GET | `/api/v1/venues/{id}/availability?sport=&date=YYYY-MM-DD&durationHours=` | Bookable start times (AVAILABLE / FULL / PAST) |

Authenticated endpoints require a Firebase ID token:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/users/me` | Get or lazily create the current profile |
| PATCH | `/api/v1/users/me` | Update `fullName`, `phone`, or `favoriteSport` |
| GET | `/api/v1/bookings/me` | List current user's bookings |
| POST | `/api/v1/bookings` | Reserve a court (`PENDING_PAYMENT`, held 30 minutes) |
| GET | `/api/v1/bookings/{id}` | Get an owned booking |
| POST | `/api/v1/bookings/{id}/payment` | Submit manual bank-transfer details (`WAITING_CONFIRMATION`) |
| PATCH | `/api/v1/bookings/{id}/cancel` | Cancel an owned booking (paid: until 2 hours before start) |

Venue-owner endpoints require a Firebase token of the venue's owner (or an admin):

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/owner/venues` | Venues the caller manages (admins: all) |
| GET | `/api/v1/owner/venues/{id}` | Venue settings, sports, owner, pending reviews |
| PATCH | `/api/v1/owner/venues/{id}` | Update phone, schedule, visibility, transfer account |
| PUT | `/api/v1/owner/venues/{id}/sports/{slug}` | Add/update a sport's hourly price and court count |
| GET | `/api/v1/owner/venues/{id}/bookings` | Bookings ending in the last 7 days or later |
| PATCH | `/api/v1/owner/bookings/{id}/confirm-payment` | Mark a booking as paid (`CONFIRMED`) |
| PATCH | `/api/v1/owner/bookings/{id}/reject-payment` | Reject the payment with a reason (`CANCELLED`) |

Admin endpoints require both a valid Firebase token and a UID listed in `ADMIN_FIREBASE_UIDS`:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/status` | Any signed-in user: returns `{admin, owner}` roles |
| GET | `/api/v1/admin/geoapify-places/search?query=...` | Search Geoapify and suggest sports/hours |
| POST | `/api/v1/admin/venues` | Publish a Geoapify venue with confirmed hours, prices, and courts |
| PUT | `/api/v1/admin/venues/{id}/owner` | Link a venue to a registered user's email (blank removes) |

## Booking Lifecycle

```text
POST /bookings ──> PENDING_PAYMENT ──(customer submits transfer)──> WAITING_CONFIRMATION ──(owner confirms)──> CONFIRMED
                        │  30 min, never past start                     │ owner rejects                          │
                        └──> EXPIRED (scheduler / lazy expiry)          └──> CANCELLED (OWNER)                   │
                   customer cancel: unpaid any time before start; submitted/paid until 2 h before start ──> CANCELLED (USER, refund flagged if money was sent)
```

`PENDING_PAYMENT`, `WAITING_CONFIRMATION` and `CONFIRMED` hold a court; the PostgreSQL exclusion constraint enforces this. `BookingExpiryScheduler` expires unpaid bookings every minute (`athlefit.bookings.expiry-check-interval-ms`), and booking creation/listing also expires overdue rows first. All booking timestamps in responses use the venue zone (`+07:00`).

Example booking request:

```http
POST /api/v1/bookings
Authorization: Bearer <firebase-id-token>
Content-Type: application/json

{
  "venueId": "10000000-0000-0000-0000-000000000001",
  "sportSlug": "badminton",
  "startAt": "2026-09-01T10:00:00+07:00",
  "durationHours": 2
}
```

The server accepts durations from one to three hours, validates the stored operating-hour snapshot, calculates the price for the selected venue/sport, and automatically assigns an available court. A `409` response means every court is occupied for the interval.

See [venue onboarding](docs/VENUE_ONBOARDING.md) for Geoapify setup, admin authorization, Swagger examples, schedule confirmation, and price/court configuration.

## Database

Flyway migrations live in `src/main/resources/db/migration`:

- `V1__create_core_schema.sql`: users, sports, venues, opening days, bookings, indexes, and overlap constraint.
- `V2__seed_demo_venues.sql`: seven sports and seven Jakarta-area demo venues.
- `V3__add_google_venues_and_courts.sql`: Google Place IDs, multi-sport prices, real court resources, and court-level overlap protection.
- `V4__replace_google_with_geoapify.sql`: provider-neutral IDs and the `GEOAPIFY` venue source.
- `V5__booking_payments_owners_and_availability.sql`: booking payment lifecycle columns/statuses, venue owners, transfer account details (demo values for manual venues), and the overlap constraint over all active statuses.

The original seven venues remain `MANUAL` and receive one court each during migration. A Geoapify venue stores an onboarding snapshot of its provider ID, location, phone, and confirmed hours in PostgreSQL. Normal venue reads and bookings therefore do not consume Geoapify quota or fail when the provider is unavailable.

Legacy Firestore documents are not imported automatically.

## Tests

```bash
./mvnw test
```

The suite checks application startup, server-side price calculation, court availability, configured court creation, Geoapify response mapping, schedule parsing, and sport suggestions. A future production hardening step should add Testcontainers coverage for the PostgreSQL exclusion constraint and authenticated controller tests.

## Related Project

The React Native app is at `/Users/eki/React Native/athlefit-main`. Its `AGENTS.md` documents the mobile compatibility mapping and local emulator URL.
