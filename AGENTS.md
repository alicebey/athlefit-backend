# Athlefit Backend Guide

Read this file first when working on the API. Keep it synchronized with the mobile app's `AGENTS.md`.

## Snapshot

- Backend root: `/Users/eki/React Native/Backend/Althefit-macro`.
- Mobile root: `/Users/eki/React Native/athlefit-main`.
- Java 17, Spring Boot 4.1, Maven wrapper, PostgreSQL 17, Flyway.
- Package root: `com.athlefit.backend`.
- Firebase Authentication remains the identity provider; Firestore is not used.
- Geoapify supplies venue candidates during admin onboarding; confirmed snapshots are stored in PostgreSQL.
- The API validates Firebase JWT signature, issuer, expiry, and project audience.
- No Firebase service-account file is needed for the current verification design.

## Commands

```bash
docker compose up -d
./mvnw spring-boot:run
./mvnw test
```

Local API: `http://localhost:8080`. Swagger: `http://localhost:8080/swagger-ui.html`.

The defaults connect to the included Compose database. Configuration keys are in `application.yml` and `.env.example`; `.env` is not automatically loaded by Spring Boot.

## Package Map

- `config/`: application beans, stateless Spring Security, Firebase decoder, and CORS.
- `controller/`: HTTP routes only; delegate business work to services.
- `dto/request/`: validated request contracts received from clients.
- `dto/response/`: stable API response contracts and entity-to-response mapping.
- `exception/`: domain-facing exceptions and consistent API error handling.
- `model/`: JPA entities and domain enums.
- `repository/`: Spring Data persistence interfaces and query definitions.
- `security/`: authenticated identity extracted from trusted Firebase JWT claims.
- `service/`: profile, venue, and booking business rules and transaction boundaries.
- `src/main/resources/db/migration/`: authoritative PostgreSQL schema and seed data.

This intentionally uses the same familiar layered organization as the reference backend, while keeping the implementation small. Keep controllers thin, put business validation in services, persistence in repositories, and hard concurrency guarantees in PostgreSQL.

## Security Boundary

Public GET routes are `/api/v1/sports`, `/api/v1/venues`, `/api/v1/venues/{id}`, OpenAPI, and Swagger. Every other route requires a valid Firebase Bearer token.

Admin routes additionally require the authenticated Firebase UID in `ADMIN_FIREBASE_UIDS`. Keep the Geoapify key server-side in `GEOAPIFY_API_KEY`; never return or add it to the mobile project.

`GET /api/v1/admin/status` is available to every authenticated user and returns only an `admin` boolean. Geoapify search and venue publishing still enforce the configured admin UID. The mobile Profile screen uses this status to reveal **Manage Venues** without persisting role state locally.

Users are keyed internally by UUID and uniquely linked by Firebase UID. Never accept a user ID from the mobile client for profile or booking ownership. `getOrCreate` lazily creates the profile from trusted token claims; profile fields can then be updated through `/api/v1/users/me`.

Do not add development authentication bypass headers. For tests, mock JWT authentication through Spring Security test support or test services directly.

## Booking Rules

- Lifecycle: `PENDING_PAYMENT` (30-minute payment window, never past start) → `WAITING_CONFIRMATION` (customer submitted manual transfer details) → `CONFIRMED` (owner/admin verified). Ends: `EXPIRED` (unpaid, set by `BookingExpiryScheduler` or lazily before create/list) or `CANCELLED` (`cancelledBy` USER or OWNER).
- Customers may cancel unpaid bookings any time before start; submitted/paid bookings only until 2 hours before start. Such cancellations set `refundRequired`.
- Duration: 1–3 hours; start at most 60 days ahead.
- Start must be in the future.
- Start/end must be on the same Asia/Jakarta calendar day.
- All venues use operating hours stored in PostgreSQL; Geoapify hours are confirmed and snapshotted during onboarding.
- Price is `venueSport.hourlyRate * duration`, calculated only by the server.
- The mobile selects a venue and sport; the server assigns an available active court.
- `PENDING_PAYMENT`, `WAITING_CONFIRMATION` and `CONFIRMED` hold a court (`BookingStatus.ACTIVE`).
- PostgreSQL's GiST exclusion constraint `no_overlapping_active_bookings` prevents overlapping active bookings per court.
- `GET /venues/{id}/availability` returns hourly start slots from opening time for a date + duration; unpaid holds past their deadline do not block.
- Booking responses return timestamps in Asia/Jakarta (`+07:00`) plus payment account, payer details, `cancellable`, `cancellableUntil` and `customer`.
- Authenticated users can only read or cancel their own bookings.

Users do not choose court numbers. Court inventory is configured during onboarding and used as booking capacity.

## Venue Owners

- `venues.owner_user_id` links a venue to one `app_users` row; admins assign it with `PUT /api/v1/admin/venues/{id}/owner {email}` (the owner must have signed up).
- `/api/v1/owner/**` (see `OwnerController`/`OwnerVenueService`): owners manage their venues; admins manage all. Owners edit phone, schedule, visibility, bank transfer account, sport price/court count (courts with upcoming active bookings cannot be removed), and confirm/reject payments.
- `GET /api/v1/admin/status` returns `{admin, owner}` for any signed-in user.

## Data and Migrations

Never edit an applied Flyway migration. Add a new versioned migration for schema or seed changes. JPA uses `ddl-auto: validate` outside tests, so migrations remain authoritative.

Current tables: `app_users`, `sports`, `venues` (incl. owner and bank transfer columns), `venue_open_days`, `venue_sports`, `venue_courts`, and `bookings` (incl. payment/cancellation columns). Demo seed data contains seven sports and seven manual venues; migration V3 gives each one a default court. Old Firestore data is not migrated.

For Geoapify venues, store the provider place ID plus a snapshot of name, address, coordinates, phone, and confirmed hours. Athlefit owns sport/price/court configuration. Do not call Geoapify during normal reads or bookings. Follow `docs/VENUE_ONBOARDING.md` and keep Geoapify/OpenStreetMap attribution visible in the mobile detail screen.

## Mobile Contract

The mobile app sends:

```json
{
  "venueId": "uuid",
  "sportSlug": "badminton",
  "startAt": "ISO-8601 timestamp with offset",
  "durationHours": 1
}
```

`GET /venues` returns each venue once with a `sports` array; top-level `category`/`hourlyRate`/`courtCount` describe the filtered (or alphabetically first) sport.

Response DTO names are modern backend names. Mobile service modules translate them into legacy screen keys. Coordinate or timestamp response changes must be coordinated with:

- `src/Service/venueService.js`
- `src/Service/bookingService.js`
- `src/Service/userService.js`
- `src/Service/ownerService.js`

## Working Rules

- Prefer standard Spring features and small domain services over extra frameworks or abstraction layers.
- Use constructor injection. Do not add field injection or Lombok solely to hide constructors.
- Do not create a service interface until there is a real second implementation or boundary that needs one.
- Name dependencies by role (`bookingRepository`, not `repository`) and methods by behavior.
- Keep API DTOs separate from JPA entities; never return entities directly from controllers.
- Preserve server-side authority and ownership checks.
- Add tests with every booking/security rule change.
- Run `./mvnw test` and, for API contract changes, the mobile Jest test and Android build.
- Keep secrets and production credentials out of source control.
- Update this guide, backend `README.md`, and mobile `AGENTS.md` when endpoints, auth, commands, or data shapes change.
