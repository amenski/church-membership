# ActivityLogController

`src/main/java/io/github/membertracker/infrastructure/ActivityLogController.java`

REST API to read the audit trail of who changed or exported what. ADMIN only. Base path `/api/activity-log`. Overview: [activity.md](activity.md).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/activity-log` | ADMIN | `limit` query parameter, 1 to 200, default 50 (`:33-34`) | `ActivityLogEntry[]`, newest first |

Paths below are relative to `src/main/java/io/github/membertracker/` unless prefixed `docs`/`frontend`.

Each item: `{ id, type, description, entityType, entityId, actor, createdAt }`. `type` is the `ActivityType` name (`domain/enumeration/ActivityType.java`; it also lists three legacy names that only the sample data wrote, so rows in an existing database never fail to read), `entityType` is `MEMBER`, `PAYMENT`, `COMMUNICATION` or `USER`, `entityId` is null for exports and sign-ins, `actor` is an email or `system`, `createdAt` is a local date-time without zone.

## Actions
- `getActivityLog` -> `GetActivityLogUseCase.invoke(limit)` (`usecase/GetActivityLogUseCase.java:17-19`) -> `ActivityLogRepository.findRecent`, which reads `PageRequest.of(0, limit)` ordered by `createdAt` then `id`, both descending (`infrastructure/persistence/repository/ActivityLogDbRepository.java:27-31`, `ActivityLogJpaRepository.java:13`). The id breaks ties between entries written in the same instant.
- Entries are written by `RecordActivityUseCase` from the member, payment, message and user use cases and from three controllers; the full list with lines is in [activity.md](activity.md#what-is-recorded).

## Collaborators
- `usecase/GetActivityLogUseCase.java`, `usecase/RecordActivityUseCase.java`
- `domain/repository/ActivityLogRepository.java` (`save`, `findRecent`), implemented by `infrastructure/persistence/repository/ActivityLogDbRepository.java`; entity `infrastructure/persistence/entity/ActivityLogEntity.java` (table `activity_log`, `activity_type` stored as the enum name, `actor` column)
- `domain/service/CurrentActor.java`, implemented by `infrastructure/security/SecurityContextCurrentActor.java:14-22`
- Beans: `infrastructure/config/UseCaseConfig.java:50-58`
- Roles: [../authentication.md](../authentication.md)

## Errors
Format: [../architecture.md](../architecture.md).

| Status | Source |
|--------|--------|
| 400 | `limit` below 1 or above 200: `errors[{field: "limit", message}]` (`infrastructure/handler/GlobalExceptionHandler.java:92-100`) |
| 401 | not authenticated |
| 403 | any role below ADMIN (`GlobalExceptionHandler.java:103-107`) |

## Side effects
None; the endpoint only reads.

## Gotchas
- Writes are best effort: a failed audit write is logged at WARN and swallowed, so an action can exist without an entry.
- No emails or phone numbers are stored, only names; an entry about a deleted member keeps the name it had.
- There is no retention or deletion job; the table only grows.
- Failed sign-ins and reads are not recorded (see [activity.md](activity.md#what-is-not-recorded)).
- `limit` is applied after sorting in the database, so a larger limit only costs more rows, not a different order.
