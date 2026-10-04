# MemberController

`src/main/java/io/github/membertracker/infrastructure/MemberController.java`

REST API for church members: list, read, create, edit, archive, permanent delete, overdue lookup and CSV export, under `/api/members`.

## Endpoints
Roles per [../authentication.md](../authentication.md). All paths are under `/api/members`; `{id}` is `@Positive`, `{months}` is `@Min(1)`.

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `` | VOLUNTEER+ (ADMIN for `?archived=true`) | optional `archived` (default false) | `List<Member>`: everyone except ARCHIVED; with `archived=true` ONLY the archived members. STAFF and VOLUNTEER get 403 for the parameter |
| GET | `/{id}` (`:77`) | VOLUNTEER+ | - | `Member` or 404 (also 404 for an ARCHIVED member unless the caller is an ADMIN, same empty body as an unknown id) |
| GET | `/active` (`:86`) | VOLUNTEER+ | - | `List<Member>` (status MEMBER) |
| GET | `/inactive` (`:93`) | VOLUNTEER+ | - | `List<Member>` (INACTIVE, DECEASED and TRANSFERRED; archived hidden) |
| POST | `` (`:100`) | STAFF+ | `MemberRequest` body, validated | saved `Member` |
| PUT | `/{id}` (`:107`) | STAFF+ | `MemberRequest` body, validated | saved `Member` or 404 |
| DELETE | `/{id}` | ADMIN | - | ARCHIVES the member (no row is deleted): 200 empty, or 404 |
| DELETE | `/{id}/permanent` | ADMIN | - | 200 empty and the row is gone; 404; or 409 `MEMBER_010` when the member has payments or message deliveries |
| GET | `/overdue/{months}` (`:129`) | VOLUNTEER+ | - | MEMBER-status members with `consecutiveMonthsMissed >= months`, longest behind first |
| GET | `/export` (`:132`) | STAFF+ | - | `members.csv`, every member except the archived |
| POST | `/export` (`:139`) | STAFF+ | `ExportMembersRequest {ids}` | `members.csv`, only the given ids; archived members are included for an ADMIN only |

CSV: a plain response built in memory (`byte[]`), `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename=members.csv`, UTF-8 with a byte order mark so Excel reads non-Latin names (Amharic) correctly (`:150-163`, `CsvUtils.attachment`). Header row: `ID,Name,Email,Phone,Join date,Months behind,Status` (plain readable headers since the contract step; the `active` column is gone, `Status` replaces it). `Join date` is an ISO local date.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllMembersUseCase` -> `findAll()` (everyone but ARCHIVED) | list, full export |
| `GetArchivedMembersUseCase` -> `findByStatus(ARCHIVED)` | list with `?archived=true` (ADMIN) | `usecase/GetAllMembersUseCase.java:17` |
| `GetMemberByIdUseCase` -> `findById` (finds archived members too) | read, the existence check in DELETE, and the selected export | `usecase/GetMemberByIdUseCase.java:23` |
| `GetActiveMembersUseCase` -> `findDuesPaying()` | `/active` | `usecase/GetActiveMembersUseCase.java:22` |
| `GetInactiveMembersUseCase` -> `findAll()` filtered to statuses that do not count for dues | `/inactive` | `usecase/GetInactiveMembersUseCase.java:22` |
| `GetMembersWithMissedPaymentsUseCase` -> `findDuesPayingWithMissedAtLeastOrderByMissedDesc` | `/overdue/{months}` | `usecase/GetMembersWithMissedPaymentsUseCase.java` |
| `SaveMemberUseCase` -> `invoke(name, email, phone, joinDate)` | POST | `usecase/SaveMemberUseCase.java:21` |
| `UpdateMemberUseCase` -> `invoke(id, name, email, phone, joinDate, status)` | PUT | `usecase/UpdateMemberUseCase.java:25` |
| `ArchiveMemberUseCase` -> `save` (status ARCHIVED, `archivedAt` now; never `deleteById`) | DELETE `/{id}` | `usecase/ArchiveMemberUseCase.java` |
| `DeleteMemberPermanentlyUseCase` -> `PaymentRepository.countByMemberId`, `MessageDeliveryRepository.countByRecipientId`, then `deleteById` | DELETE `/{id}/permanent` | `usecase/DeleteMemberPermanentlyUseCase.java` |
| `CsvUtils.escapeCsv` | name, email, phone cells | `utils/CsvUtils.java:26` |
| `MemberRequest` | POST, PUT body | `infrastructure/dto/MemberRequest.java:15` |
| `ExportMembersRequest` | POST `/export` | `infrastructure/dto/ExportMembersRequest.java:9` |

- Responses return the domain `Member` (`domain/model/Member.java:13`). Requests use `MemberRequest`: `name`, `email?`, `phone?`, `joinDate?`, `status?` (MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED). The JSON of a member carries `status` and `archivedAt`; there is no `active` (it was removed at step 12, a client that still sends it is ignored like any unknown property). It has no `id`, `consecutiveMonthsMissed`, `lastPaymentDate` or `lastMissedCountMonth`; unknown JSON properties are ignored (Spring Boot default), so a client cannot set them (audit C8; `MemberContractTest`). Contract fixture: `src/test/resources/contracts/member-request.json`, shared with the frontend test.
- Validation on `MemberRequest`: name required, max 100; email optional (trimmed, blank becomes null), email format when present, max 100; phone optional (blank becomes null) and, if present, `^\+?[0-9\s\-\(\)]{10,}$`; `joinDate` not in the future (`MemberRequest.java`, blank-phone and blank-email setters).
- `ExportMembersRequest.ids`: not empty, max 5000, each positive (`ExportMembersRequest.java:11-13`).
- Business methods on `Member` (`recordPayment` `:46`, `activate` `:73`, `deactivate` `:81`) are not called by this controller directly. `recordPayment` is used by `RecordPaymentUseCase`, and `activate()`/`deactivate()` by `UpdateMemberUseCase`. The monthly job uses `markMissedFor` (`:64`). The unused `markPaymentMissed`, `isPaymentOverdue`, `isValid` and `getMembershipDurationInMonths` were removed in `chore: remove unused domain methods`: the member JSON no longer carries `valid`, `paymentOverdue` or `membershipDurationInMonths` (the frontend never read them).
- `SaveMemberUseCase` (POST): builds a new `Member` (any email, including none or one another member has; no duplicate check): `joinDate` defaults to today, counters zero. The status is `status` from the body, else MEMBER; only MEMBER or INACTIVE are accepted on create: DECEASED, TRANSFERRED or ARCHIVED answer 400 `MEMBER_009` with a field error on `status`.
- `UpdateMemberUseCase` (PUT): loads the stored member (empty -> 404), copies name, email and phone, copies `joinDate` only when sent, and applies the status through the domain: to MEMBER calls `Member.activate()` (from any other status; resets `consecutiveMonthsMissed` to 0), to INACTIVE calls `deactivate()`, to DECEASED or TRANSFERRED sets it directly (counter frozen), same status or none given changes nothing. ARCHIVED is refused with 400 `MEMBER_009` and a field error on `status` (archiving is `DELETE /{id}`). The activity log gets MEMBER_UPDATED (naming the new status when it changed) and, when the member started or stopped counting for dues, MEMBER_ACTIVATED or MEMBER_DEACTIVATED. It never touches `lastPaymentDate`, `consecutiveMonthsMissed` or `lastMissedCountMonth` (`UpdateMemberUseCase.java:25-61`).
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
| 409 | Permanent delete of a member with payments or message deliveries (`MEMBER_010`; `DomainException.isConflict()`) | `GlobalExceptionHandler.handleDomainException` |
| 404 | Unknown id on GET `/{id}`, PUT, DELETE (an archived member is also unknown to a non-admin) | `MemberController.java:87`, `:118`, `:129` |
| 500 | Anything else, message "An unexpected error occurred" | `GlobalExceptionHandler.java:110` |

## Side effects
- DELETE only archives: payments and delivery history are untouched. Since migration `011` the foreign keys `fk_payment_member` and `fk_delivery_member` are `ON DELETE RESTRICT`, so even a raw SQL delete of a member with history fails (MySQL 1451).
- Create, edit (plus activate or deactivate when the status changed), delete and both CSV exports add entries to the activity log: `SaveMemberUseCase.java:30`, `UpdateMemberUseCase.java:51`, `:55`, `ArchiveMemberUseCase.java` (MEMBER_ARCHIVED), `DeleteMemberPermanentlyUseCase.java` (MEMBER_DELETED), `MemberController.java`. Names only, written best effort after the action: [activity.md](activity.md).
- No emails, jobs or caches.

## Gotchas
- Audit C9 is closed: no endpoint erases payments or deliveries any more. A PUT on an archived member by STAFF is a 404 (only an ADMIN can see or restore one); restoring to MEMBER resets the missed-months counter, to INACTIVE keeps it frozen.
- Email is optional and not unique since migration `009` (audit C10): two members can share an address and a member can have none. Rolling migration 009 back fails while any row has a NULL or a duplicate email, on purpose. A child added without an email counts as a member until the status step of the [person/membership plan](../person-membership-plan.md): they appear behind on dues unless marked inactive.
- 404 responses are empty bodies, not `ProblemDetail` (`MemberController.java:87`, `:118`, `:129`).
- `active` no longer exists: not in the JSON, not in `MemberRequest`, not in the CSV, not in the database (migration `014`). Reactivating is `status: MEMBER` and resets the missed-months counter.
- There is no email lookup any more (`findByEmailIgnoreCase`/`existsByEmailIgnoreCase` were removed with the duplicate check); the `member.email` column and its `idx_member_email_lookup` index were dropped by migration `014` (email lives on `person`, which keeps `idx_person_email`).
- POST `/export` looks each distinct id up with `GetMemberByIdUseCase` (sorted by id) and drops archived members unless the caller is an ADMIN (`ArchivedVisibility`, the one rule for every read path); ids that do not exist are silently skipped.
- The CSV is fully built before the response starts, so a failure gives a normal 500 instead of a half-written file.
