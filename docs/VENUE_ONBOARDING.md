# Geoapify Venue Onboarding

This guide covers the admin flow for finding a sports venue through Geoapify, confirming its operating hours and sports, setting Athlefit prices and court counts, and publishing it.

## Data Ownership and Runtime Behavior

Geoapify is used only during admin search and onboarding. When a venue is published, Athlefit stores a snapshot of its provider ID, name, address, coordinates, phone, and confirmed operating hours in PostgreSQL. Normal venue reads and bookings do not call Geoapify, so they do not consume provider quota and remain available if Geoapify is temporarily unavailable.

Athlefit owns sports, hourly prices, court inventory, availability, and bookings. Existing Flyway demo venues remain `MANUAL`; imported venues use `GEOAPIFY`.

Geoapify does not supply the Google rating/photo fields previously used by the app. Imported venues currently display the app's fallback image and no star rating.

## 1. Create a Geoapify Key

1. Create a free account at [Geoapify My Projects](https://myprojects.geoapify.com/).
2. Create a project and copy its API key.
3. Keep the key on the Spring Boot backend. Do not put it in React Native, Git, `application.yml`, or `google-services.json`.

Geoapify currently advertises a free plan with 3,000 credits per day and no credit card required. Check the [official pricing page](https://www.geoapify.com/pricing/) before production use because limits can change.

## 2. Configure the Backend and Admin UID

Open Firebase Console, go to **Authentication > Users**, and copy the UID of the account that will manage venues. This is the Firebase UID, not its email address. Multiple admins can be configured as a comma-separated list.

Shell setup:

```bash
export GEOAPIFY_API_KEY="your-server-side-key"
export ADMIN_FIREBASE_UIDS="your-firebase-uid"
./mvnw spring-boot:run
```

For IntelliJ IDEA:

1. Open **Run > Edit Configurations**.
2. Select the Athlefit Spring Boot configuration.
3. Add `GEOAPIFY_API_KEY` and `ADMIN_FIREBASE_UIDS` under **Environment variables**.
4. Restart the application.

`.env.example` lists these values, but Spring Boot does not automatically load a `.env` file.

## 3. Use the Athlefit Admin Screen

After restarting Spring Boot and the mobile app, log in with a UID listed in `ADMIN_FIREBASE_UIDS`:

1. Open **Profile**.
2. Tap **Manage Venues**. This button is hidden for non-admin users.
3. Search for a venue, sport, and city, such as `futsal jakarta`.
4. Select a candidate and review its address and provider hours.
5. Select every sport the venue offers, then enter each hourly price and court count.
6. Confirm opening days and hours.
7. Tap **Publish Venue**. The venue becomes visible to customer category and search screens immediately.

The app sends the same authenticated admin API requests documented below. Swagger remains useful for debugging but is not required for normal onboarding.

## 4. Authorize Swagger (Optional)

Open `http://localhost:8080/swagger-ui.html` and click **Authorize**. Paste a Firebase ID token for a UID included in `ADMIN_FIREBASE_UIDS`. Paste only the token; Swagger adds the `Bearer` prefix.

Treat an ID token like a temporary password. Do not paste it into source files or commit it.

## 5. Search and Review a Venue

Call:

```http
GET /api/v1/admin/geoapify-places/search?query=lapangan badminton jakarta
Authorization: Bearer <firebase-id-token>
```

The response contains:

- `geoapifyPlaceId`, name, address, and coordinates;
- Geoapify categories and OpenStreetMap sport tags;
- raw `openingHours` when available;
- `suggestedSchedule` when the provider hours can be represented by Athlefit's current one-time-range schedule;
- suggested Athlefit sport slugs.

Suggestions are a starting point. The admin must review sports and hours before publishing. Provider data can be incomplete or stale.

## 6. Publish Prices and Court Capacity

When `suggestedSchedule` is present and correct, publish with:

```json
{
  "geoapifyPlaceId": "provider-place-id",
  "scheduleOverride": null,
  "sports": [
    {
      "sportSlug": "badminton",
      "hourlyRate": 75000,
      "courtCount": 4
    },
    {
      "sportSlug": "futsal",
      "hourlyRate": 150000,
      "courtCount": 2
    }
  ]
}
```

If provider hours are missing, too complex, or incorrect, send an explicit override:

```json
{
  "geoapifyPlaceId": "provider-place-id",
  "scheduleOverride": {
    "openDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
    "openTime": "08:00",
    "closeTime": "22:00"
  },
  "sports": [
    {
      "sportSlug": "badminton",
      "hourlyRate": 75000,
      "courtCount": 4
    }
  ]
}
```

Call `POST /api/v1/admin/venues` with that body. Rules:

- `sportSlug` must already exist in the `sports` table and cannot repeat in one request.
- `hourlyRate` must be at least `1`.
- `courtCount` must be between `1` and `50`.
- A Geoapify place ID can only be onboarded once.
- The current schema supports one open/close range shared by the selected days. Use `scheduleOverride` when Geoapify's value cannot be simplified safely.

Price and court count deliberately remain admin-owned because public map providers normally do not know the venue's actual bookable inventory or Athlefit price. The backend creates `Court 1`, `Court 2`, and so on automatically. Users choose only a venue and sport; the backend assigns an available court.

## Booking Behavior

For every booking, the server:

1. resolves the configured venue/sport price;
2. validates the stored operating-hour snapshot;
3. finds an available court for the full interval;
4. calculates and snapshots the total price;
5. creates the booking, or returns `409` when all courts are occupied.

To change hours after onboarding, add an authenticated admin update endpoint rather than silently refreshing provider data. That keeps business decisions reviewable and prevents unexpected schedule changes from invalidating bookings.

## Attribution and Provider Limits

Responses for imported venues include `Powered by Geoapify` and `© OpenStreetMap contributors` links. The mobile detail screen displays both. Preserve these fields if the response or UI is refactored.

Official references:

- [Geoapify Places API](https://apidocs.geoapify.com/docs/places/)
- [Geoapify Place Details API](https://apidocs.geoapify.com/docs/place-details/)
- [Geoapify pricing](https://www.geoapify.com/pricing/)
- [Geoapify terms and attribution](https://www.geoapify.com/terms-and-conditions/)
