# MemberController

`src/main/java/io/github/membertracker/infrastructure/MemberController.java`

REST API for church members: list, read, create, edit, delete, overdue lookup and CSV export, under `/api/members`.

## Endpoints
Roles per [../authentication.md](../authentication.md). All paths are under `/api/members`; `{id}` is `@Positive`, `{months}` is `@Min(1)`.

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `` (`MemberController.java:74`) | VOLUNTEER+ | - | `List<Member>` |
| GET | `/{id}` (`:77`) | VOLUNTEER+ | - | `Member` or 404 |
| GET | `/active` (`:86`) | VOLUNTEER+ | - | `List<Member>` (active = true) |
| GET | `/inactive` (`:93`) | VOLUNTEER+ | - | `List<Member>` (active = false) |
| POST | `` (`:100`) | STAFF+ | `MemberRequest` body, validated | saved `Member` |
| PUT | `/{id}` (`:107`) | STAFF+ | `MemberRequest` body, validated | saved `Member` or 404 |
| DELETE | `/{id}` (`:117`) | ADMIN | - | 200 empty, or 404 |
| GET | `/overdue/{months}` (`:129`) | VOLUNTEER+ | - | ACTIVE members with `consecutiveMonthsMissed >= months`, longest behind first |
| GET | `/export` (`:132`) | STAFF+ | - | `members.csv`, all members |
| POST | `/export` (`:139`) | STAFF+ | `ExportMembersRequest {ids}` | `members.csv`, only the given ids |

CSV: a plain response built in memory (`byte[]`), `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename=members.csv`, UTF-8 with a byte order mark so Excel reads non-Latin names (Amharic) correctly (`:150-163`, `CsvUtils.attachment`). Columns: `id,name,email,phone,joinDate,active,consecutiveMonthsMissed`. `joinDate` is ISO local date.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllMembersUseCase` -> `findAll()` | list, both exports | `usecase/GetAllMembersUseCase.java:17` |
| `GetMemberByIdUseCase` -> `findById` | read, and the existence check in DELETE | `usecase/GetMemberByIdUseCase.java:23` |
| `GetActiveMembersUseCase` -> `findByActive(true)` | `/active` | `usecase/GetActiveMembersUseCase.java:22` |
| `GetInactiveMembersUseCase` -> `findByActive(false)` | `/inactive` | `usecase/GetInactiveMembersUseCase.java:22` |
| `GetMembersWithMissedPaymentsUseCase` -> `findActiveWithMissedAtLeastOrderByMissedDesc` | `/overdue/{months}` | `usecase/GetMembersWithMissedPaymentsUseCase.java` |
| `SaveMemberUseCase` -> `invoke(name, email, phone, joinDate)` | POST | `usecase/SaveMemberUseCase.java:21` |
| `UpdateMemberUseCase` -> `invoke(id, name, email, phone, joinDate, status, active)` | PUT | `usecase/UpdateMemberUseCase.java:25` |
| `DeleteMemberUseCase` -> `deleteById` | DELETE | `usecase/DeleteMemberUseCase.java:21` |
| `CsvUtils.escapeCsv` | name, email, phone cells | `utils/CsvUtils.java:26` |
| `MemberRequest` | POST, PUT body | `infrastructure/dto/MemberRequest.java:15` |
| `ExportMembersRequest` | POST `/export` | `infrastructure/dto/ExportMembersRequest.java:9` |

- Responses return the domain `Member` (`domain/model/Member.java:13`). Requests use `MemberRequest`: `name`, `email?`, `phone?`, `joinDate?`, `status?` (MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED), `active?`. The JSON of a member carries `status` and `active` (derived: true only for MEMBER) and `archivedAt`. It has no `id`, `consecutiveMonthsMissed`, `lastPaymentDate` or `lastMissedCountMonth`; unknown JSON properties are ignored (Spring Boot default), so a client cannot set them (audit C8; `MemberContractTest`). Contract fixture: `src/test/resources/contracts/member-request.json`, shared with the frontend test.
- Validation on `MemberRequest`: name required, max 100; email optional (trimmed, blank becomes null), email format when present, max 100; phone optional (blank becomes null) and, if present, `^\+?[0-9\s\-\(\)]{10,}$`; `joinDate` not in the future (`MemberRequest.java`, blank-phone and blank-email setters).
- `ExportMembersRequest.ids`: not empty, max 5000, each positive (`ExportMembersRequest.java:11-13`).
- Business methods on `Member` (`recordPayment` `:46`, `activate` `:73`, `deactivate` `:81`) are not called by this controller directly. `recordPayment` is used by `RecordPaymentUseCase`, and `activate()`/`deactivate()` by `UpdateMemberUseCase`. The monthly job uses `markMissedFor` (`:64`). The unused `markPaymentMissed`, `isPaymentOverdue`, `isValid` and `getMembershipDurationInMonths` were removed in `chore: remove unused domain methods`: the member JSON no longer carries `valid`, `paymentOverdue` or `membershipDurationInMonths` (the frontend never read them).
- `SaveMemberUseCase` (POST): builds a new `Member` (any email, including none or one another member has; no duplicate check): `joinDate` defaults to today, counters zero. The status is `status` from the body, else the legacy `active` (true -> MEMBER, false -> INACTIVE), else MEMBER; only MEMBER or INACTIVE are accepted on create: DECEASED, TRANSFERRED or ARCHIVED answer 400 `MEMBER_009` with a field error on `status`.
- `UpdateMemberUseCase` (PUT): loads the stored member (empty -> 404), copies name, email and phone, copies `joinDate` only when sent, and applies the status through the domain: `status` wins over `active`; to MEMBER calls `Member.activate()` (from any other status; resets `consecutiveMonthsMissed` to 0), to INACTIVE calls `deactivate()`, to DECEASED or TRANSFERRED sets it directly (counter frozen), same status or none given changes nothing. ARCHIVED is refused with 400 `MEMBER_009` and a field error on `status` (archiving is a separate step, not built yet). The legacy `active` (used only when `status` is absent) turns any non-member into a member when true and a member into INACTIVE when false, and never changes someone who is already off, so an old client cannot turn a deceased person into an inactive one. The activity log gets MEMBER_UPDATED (naming the new status when it changed) and, when the member started or stopped counting for dues, MEMBER_ACTIVATED or MEMBER_DEACTIVATED. It never touches `lastPaymentDate`, `consecutiveMonthsMissed` or `lastMissedCountMonth` (`UpdateMemberUseCase.java:25-61`).
- `CsvUtils.escapeCsv`: null -> empty; a leading `= + - @ TAB CR` gets a `'` prefix unless the value looks like a phone number or plain number (`CsvUtils.java:31-34`); values with `,` `"` or newline are quoted (`:35-37`).

## Errors
All are RFC 7807 `ProblemDetail` ([../architecture.md](../architecture.md)), except 404s.

| Status | Cause | Source |
|--------|-------|--------|
| 400 | Invalid `MemberRequest` or `ExportMembersRequest` body: "One or more fields are invalid" + `errors[]` | `src/main/java/io/github/membertracker/infrastructure/handler/GlobalExceptionHandler.java:53` |
| 400 | A status the request may not set: ARCHIVED on PUT, DECEASED, TRANSFERRED or ARCHIVED on POST (`MEMBER_009`, with `errors[{field: "status"}]`) | `GlobalExceptionHandler.handleDomainException` |
| 400 | Bad `{id}` (not positive) or `{months}` (< 1) | `GlobalExceptionHandler.java:84` |
| 409 | A database constraint fails: "The request conflicts with existing data", never the SQL text | `GlobalExceptionHandler.java:77` |
| 400 | Malformed JSON | `GlobalExceptionHandler.java:43` (Spring base class) |
| 401 | Not authenticated | `GlobalExceptionHandler.java:102` |
| 403 | Role too low (`@PreAuthorize`) | `GlobalExceptionHandler.java:95` |
| 404 | Unknown id on GET `/{id}`, PUT, DELETE | `MemberController.java:87`, `:118`, `:129` |
| 500 | Anything else, message "An unexpected error occurred" | `GlobalExceptionHandler.java:110` |

## Side effects
- DELETE cascades to the member's payments and delivery history (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`).
- Create, edit (plus activate or deactivate when the status changed), delete and both CSV exports add entries to the activity log: `SaveMemberUseCase.java:30`, `UpdateMemberUseCase.java:51`, `:55`, `DeleteMemberUseCase.java:25`, `MemberController.java:156`. Names only, written best effort after the action: [activity.md](activity.md).
- No emails, jobs or caches.

## Gotchas
- Hard delete erases payments and delivery history (`MemberController.java:125-127`) (audit C9).
- Email is optional and not unique since migration `009` (audit C10): two members can share an address and a member can have none. Rolling migration 009 back fails while any row has a NULL or a duplicate email, on purpose. A child added without an email counts as a member until the status step of the [person/membership plan](../person-membership-plan.md): they appear behind on dues unless marked inactive.
- 404 responses are empty bodies, not `ProblemDetail` (`MemberController.java:87`, `:118`, `:129`).
- `active` is the legacy form of `status`: on POST `active: false` creates an INACTIVE member, on PUT it is applied as described above (reactivating resets the missed-months counter). `status` wins when both are sent. The `active` database column is kept in step by `MemberPersistenceMapper` (its only writer) until the contract step.
- There is no email lookup any more (`findByEmailIgnoreCase`/`existsByEmailIgnoreCase` were removed with the duplicate check); `idx_member_email_lookup` is a plain index for future lookups.
- POST `/export` loads all members and filters in memory; ids that do not exist are silently skipped (`MemberController.java:146-151`).
- The CSV is fully built before the response starts, so a failure gives a normal 500 instead of a half-written file.
