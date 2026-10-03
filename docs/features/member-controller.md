# MemberController

`src/main/java/io/github/membertracker/infrastructure/MemberController.java`

REST API for church members: list, read, create, edit, delete, overdue lookup and CSV export, under `/api/members`.

## Endpoints
Roles per [../authentication.md](../authentication.md). All paths are under `/api/members`; `{id}` is `@Positive`, `{months}` is `@Min(1)`.

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `` (`MemberController.java:66`) | VOLUNTEER+ | - | `List<Member>` |
| GET | `/{id}` (`:73`) | VOLUNTEER+ | - | `Member` or 404 |
| GET | `/active` (`:82`) | VOLUNTEER+ | - | `List<Member>` (active = true) |
| GET | `/inactive` (`:89`) | VOLUNTEER+ | - | `List<Member>` (active = false) |
| POST | `` (`:96`) | STAFF+ | `Member` body, validated | saved `Member` |
| PUT | `/{id}` (`:103`) | STAFF+ | `Member` body, validated | saved `Member` or 404 |
| DELETE | `/{id}` (`:115`) | ADMIN | - | 200 empty, or 404 |
| GET | `/overdue/{months}` (`:127`) | VOLUNTEER+ | - | members with `consecutiveMonthsMissed >= months` |
| GET | `/export` (`:134`) | VOLUNTEER+ | - | `members.csv`, all members |
| POST | `/export` (`:141`) | VOLUNTEER+ | `ExportMembersRequest {ids}` | `members.csv`, only the given ids |

CSV: `text/csv`, `Content-Disposition: attachment; filename=members.csv`, streamed (`:181-184`). Columns: `id,name,email,phone,joinDate,active,consecutiveMonthsMissed` (`:159`). `joinDate` is ISO local date.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllMembersUseCase` -> `findAll()` | list, both exports | `usecase/GetAllMembersUseCase.java:17` |
| `GetMemberByIdUseCase` -> `findById` | read, and the existence check in PUT and DELETE | `usecase/GetMemberByIdUseCase.java:23` |
| `GetActiveMembersUseCase` -> `findByActive(true)` | `/active` | `usecase/GetActiveMembersUseCase.java:22` |
| `GetInactiveMembersUseCase` -> `findByActive(false)` | `/inactive` | `usecase/GetInactiveMembersUseCase.java:22` |
| `GetMembersWithMissedPaymentsUseCase` -> `findByConsecutiveMonthsMissedGreaterThanEqual` | `/overdue/{months}` | `usecase/GetMembersWithMissedPaymentsUseCase.java:23` |
| `SaveMemberUseCase` -> `save` | POST, PUT | `usecase/SaveMemberUseCase.java:16` |
| `DeleteMemberUseCase` -> `deleteById` | DELETE | `usecase/DeleteMemberUseCase.java:18` |
| `CsvUtils.escapeCsv` | name, email, phone cells | `utils/CsvUtils.java:26` |
| `ExportMembersRequest` | POST `/export` | `infrastructure/dto/ExportMembersRequest.java:9` |

- `Member` (`domain/model/Member.java:13`) is the request and response body (no separate DTO).
- Validation on `Member`: name and email required, email format, phone `^\+?[0-9\s\-\(\)]{10,}$` (optional), `joinDate` not in the future (`Member.java:17-28`).
- `ExportMembersRequest.ids`: not empty, max 5000, each positive (`ExportMembersRequest.java:11-13`).
- Business methods on `Member` (`recordPayment` `:46`, `markPaymentMissed` `:59`, `activate` `:63`, `deactivate` `:71`, `isPaymentOverdue` `:78`, `getMembershipDurationInMonths` `:88`) are not called by this controller. Payment and reminder flows use them.
- `SaveMemberUseCase` on create (id null): `joinDate` defaults to today, `active` is forced to `true` (`SaveMemberUseCase.java:18-26`).
- `CsvUtils.escapeCsv`: null -> empty; a leading `= + - @ TAB CR` gets a `'` prefix unless the value looks like a phone number or plain number (`CsvUtils.java:31-34`); values with `,` `"` or newline are quoted (`:35-37`).

## Errors
All are RFC 7807 `ProblemDetail` ([../architecture.md](../architecture.md)), except 404s.

| Status | Cause | Source |
|--------|-------|--------|
| 400 | Invalid `Member` or `ExportMembersRequest` body: "One or more fields are invalid" + `errors[]` | `GlobalExceptionHandler.java:52` |
| 400 | Bad `{id}` (not positive) or `{months}` (< 1) | `GlobalExceptionHandler.java:76` |
| 400 | Malformed JSON | `GlobalExceptionHandler.java:42` (Spring base class) |
| 401 | Not authenticated | `GlobalExceptionHandler.java:94` |
| 403 | Role too low (`@PreAuthorize`) | `GlobalExceptionHandler.java:87` |
| 404 | Unknown id on GET `/{id}`, PUT, DELETE | `MemberController.java:79`, `:112`, `:123` |
| 500 | Anything else, message "An unexpected error occurred" | `GlobalExceptionHandler.java:102` |

## Side effects
- DELETE cascades to the member's payments and delivery history (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`).
- No emails, jobs or caches.

## Gotchas
- PUT and POST bind the whole domain object, so a client can set `active`, `consecutiveMonthsMissed` and `lastPaymentDate` (`MemberController.java:99`, `:106`) (audit C8).
- Hard delete erases payments and delivery history (`MemberController.java:119-121`) (audit C9).
- Email is unique (`001.schema-creation.sql:19`) and there is no handler for the duplicate-key error, so a duplicate email returns 500, not 400 (`GlobalExceptionHandler.java:102`) (audit C10).
- 404 responses are empty bodies, not `ProblemDetail` (`MemberController.java:79`, `:112`, `:123`).
- POST ignores `active: false`: new members are always active (`SaveMemberUseCase.java:23-25`). PUT keeps the sent value.
- POST `/export` loads all members and filters in memory; ids that do not exist are silently skipped (`MemberController.java:145-148`).
- A failure while streaming the CSV is logged to stderr and rethrown after the 200 headers may be sent (`MemberController.java:173-175`).
