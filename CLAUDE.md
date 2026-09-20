# Claude Code Project Context — Athlefit backend

@AGENTS.md

End-to-end map of the whole system (mobile + backend + DB, flows, gotchas): `../../CLAUDE.md`.

## Backend quick orientation (Claude notes)

- Request path: `controller/*` → `service/*` → `repository/*`; identity is always `AuthenticatedUser.from(jwt)`.
- Booking core: `BookingService.create` → `VenueService.validateOperatingHours` → `CourtRepository.findAvailable`
  → save; the GiST exclusion constraint on `bookings(court_id, tstzrange)` is the final race guard (→ 409).
- `GET /venues` returns one item per venue with a `sports[]` list; availability at `GET /venues/{id}/availability`.
- Admin = Firebase UID listed in `ADMIN_FIREBASE_UIDS`; Geoapify only via `GeoapifyClient` during onboarding.
- Tests use H2 (PostgreSQL mode) with Flyway disabled — DB constraints/migrations are not covered by `./mvnw test`.
- Schema changes: new `src/main/resources/db/migration/V6__*.sql`; never edit V1–V5.
