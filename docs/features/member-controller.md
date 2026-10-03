# MemberController

`src/main/java/io/github/membertracker/infrastructure/MemberController.java`

REST API for church members: list, read, create, edit, delete, overdue lookup and CSV export, under `/api/members`.

## Endpoints
Roles per [../authentication.md](../authentication.md). All paths are under `/api/members`; `{id}` is `@Positive`, `{months}` is `@Min(1)`.

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `` (`MemberController.java:70`) | VOLUNTEER+ | - | `List<Member>` |
| GET | `/{id}` (`:77`) | VOLUNTEER+ | - | `Member` or 404 |
| GET | `/active` (`:86`) | VOLUNTEER+ | - | `List<Member>` (active = true) |
| GET | `/inactive` (`:93`) | VOLUNTEER+ | - | `List<Member>` (active = false) |
| POST | `` (`:100`) | STAFF+ | `MemberRequest` body, validated | saved `Member` |
| PUT | `/{id}` (`:107`) | STAFF+ | `MemberRequest` body, validated | saved `Member` or 404 |
| DELETE | `/{id}` (`:117`) | ADMIN | - | 200 empty, or 404 |
| GET | `/overdue/{months}` (`:129`) | VOLUNTEER+ | - | members with `consecutiveMonthsMissed >= months` |
| GET | `/export` (`:132`) | VOLUNTEER+ | - | `members.csv`, all members |
| POST | `/export` (`:139`) | VOLUNTEER+ | `ExportMembersRequest {ids}` | `members.csv`, only the given ids |

CSV: a plain response built in memory (`byte[]`), `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename=members.csv`, UTF-8 with a byte order mark so Excel reads non-Latin names (Amharic) correctly (`:150-163`, `CsvUtils.attachment`). Columns: `id,name,email,phone,joinDate,active,consecutiveMonthsMissed`. `joinDate` is ISO local date.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllMembersUseCase` -> `findAll()` | list, both exports | `usecase/GetAllMembersUseCase.java:17` |
| `GetMemberByIdUseCase` -> `findById` | read, and the existence check in DELETE | `usecase/GetMemberByIdUseCase.java:23` |
| `GetActiveMembersUseCase` -> `findByActive(true)` | `/active` | `usecase/GetActiveMembersUseCase.java:22` |
| `GetInactiveMembersUseCase` -> `findByActive(false)` | `/inactive` | `usecase/GetInactiveMembersUseCase.java:22` |
| `GetMembersWithMissedPaymentsUseCase` -> `findByConsecutiveMonthsMissedGreaterThanEqual` | `/overdue/{months}` | `usecase/GetMembersWithMissedPaymentsUseCase.java:23` |
| `SaveMemberUseCase` -> `invoke(name, email, phone, joinDate)` | POST | `usecase/SaveMemberUseCase.java:18` |
| `UpdateMemberUseCase` -> `invoke(id, name, email, phone, joinDate, active)` | PUT | `usecase/UpdateMemberUseCase.java:22` |
| `DeleteMemberUseCase` -> `deleteById` | DELETE | `usecase/DeleteMemberUseCase.java:18` |
| `CsvUtils.escapeCsv` | name, email, phone cells | `utils/CsvUtils.java:26` |
| `MemberRequest` | POST, PUT body | `infrastructure/dto/MemberRequest.java:15` |
| `ExportMembersRequest` | POST `/export` | `infrastructure/dto/ExportMembersRequest.java:9` |

- Responses return the domain `Member` (`domain/model/Member.java:13`). Requests use `MemberRequest`: `name`, `email`, `phone?`, `joinDate?`, `active?`. It has no `id`, `consecutiveMonthsMissed`, `lastPaymentDate` or `lastMissedCountMonth`; unknown JSON properties are ignored (Spring Boot default), so a client cannot set them (audit C8; `MemberContractTest`). Contract fixture: `src/test/resources/contracts/member-request.json`, shared with the frontend test.
- Validation on `MemberRequest`: name required, max 100; email required, email format, max 100; phone optional (blank becomes null) and, if present, `^\+?[0-9\s\-\(\)]{10,}$`; `joinDate` not in the future (`MemberRequest.java:17-31`, blank-phone setter `:59`).
- `ExportMembersRequest.ids`: not empty, max 5000, each positive (`ExportMembersRequest.java:11-13`).
- Business methods on `Member` (`recordPayment` `:46`, `activate` `:73`, `deactivate` `:81`) are not called by this controller directly. `recordPayment` is used by `RecordPaymentUseCase`, and `activate()`/`deactivate()` by `UpdateMemberUseCase`. The monthly job uses `markMissedFor` (`:64`). The unused `markPaymentMissed`, `isPaymentOverdue`, `isValid` and `getMembershipDurationInMonths` were removed in `chore: remove unused domain methods`: the member JSON no longer carries `valid`, `paymentOverdue` or `membershipDurationInMonths` (the frontend never read them).
- `SaveMemberUseCase` (POST): rejects an email that exists in any case (400, `MEMBER_007`, "A member with this email already exists"), then builds a new `Member`: `joinDate` defaults to today, `active` is always `true`, counters zero (`SaveMemberUseCase.java:18-26`). `active` in the body is ignored.
- `UpdateMemberUseCase` (PUT): loads the stored member (empty -> 404), rejects an email that belongs to a different member (the member's own email is fine), copies name, email and phone, copies `joinDate` only when sent, and applies `active` through the domain: false -> true calls `Member.activate()` (resets `consecutiveMonthsMissed` to 0), true -> false calls `deactivate()`, same state or absent changes nothing. It never touches `lastPaymentDate`, `consecutiveMonthsMissed` or `lastMissedCountMonth` (`UpdateMemberUseCase.java:22-47`).
- `CsvUtils.escapeCsv`: null -> empty; a leading `= + - @ TAB CR` gets a `'` prefix unless the value looks like a phone number or plain number (`CsvUtils.java:31-34`); values with `,` `"` or newline are quoted (`:35-37`).

## Errors
All are RFC 7807 `ProblemDetail` ([../architecture.md](../architecture.md)), except 404s.

| Status | Cause | Source |
|--------|-------|--------|
| 400 | Invalid `MemberRequest` or `ExportMembersRequest` body: "One or more fields are invalid" + `errors[]` | `src/main/java/io/github/membertracker/infrastructure/handler/GlobalExceptionHandler.java:53` |
| 400 | Bad `{id}` (not positive) or `{months}` (< 1) | `GlobalExceptionHandler.java:84` |
| 400 | Duplicate email on POST or PUT (domain exception `MEMBER_007`, "A member with this email already exists") | `GlobalExceptionHandler.java:69` |
| 409 | A database constraint (such as the unique email index) fails after the checks, e.g. a race: "The request conflicts with existing data", never the SQL text | `GlobalExceptionHandler.java:77` |
| 400 | Malformed JSON | `GlobalExceptionHandler.java:43` (Spring base class) |
| 401 | Not authenticated | `GlobalExceptionHandler.java:102` |
| 403 | Role too low (`@PreAuthorize`) | `GlobalExceptionHandler.java:95` |
| 404 | Unknown id on GET `/{id}`, PUT, DELETE | `MemberController.java:83`, `:114`, `:125` |
| 500 | Anything else, message "An unexpected error occurred" | `GlobalExceptionHandler.java:110` |

## Side effects
- DELETE cascades to the member's payments and delivery history (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`).
- No emails, jobs or caches.

## Gotchas
- Hard delete erases payments and delivery history (`MemberController.java:121-123`) (audit C9).
- Email is unique in the database (`001.schema-creation.sql:19`) and one email per member is a model limit (audit C10). The use cases check duplicates first; the unique index is the backstop (409).
- 404 responses are empty bodies, not `ProblemDetail` (`MemberController.java:83`, `:114`, `:125`).
- POST ignores `active: false`: new members are always active (`SaveMemberUseCase.java:22`). PUT applies `active` (reactivating resets the missed-months counter).
- The email lookup is `findByEmailIgnoreCase`/`existsByEmailIgnoreCase`; check-then-save is not atomic, hence the 409 safety net.
- POST `/export` loads all members and filters in memory; ids that do not exist are silently skipped (`MemberController.java:143-147`).
- The CSV is fully built before the response starts, so a failure gives a normal 500 instead of a half-written file.
