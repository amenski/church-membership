# PaymentController

`src/main/java/io/github/membertracker/infrastructure/PaymentController.java`

Membership-dues API: list (all, or one page), paid months, look up, record and export payments. Roles per endpoint below. The CSV export is STAFF and above (it carries every member's name and amounts).

Archived members: for a non-ADMIN caller a payment that embeds an ARCHIVED member keeps the member's id, name and status but loses the email and phone (`GET /api/payments`, `/page`, `/{id}`, and the dashboard's recent payments; `/paid-months` carries member ids and months only), and `/member/{memberId}` is a 404 as if the member did not exist (`ArchivedVisibility`, `ArchivedVisibilityTest`).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/payments` | VOLUNTEER+ (`PaymentController.java:75`) | none | `List<Payment>`: every payment, a plain array. Kept for the screens and exports that need all of them |
| GET | `/api/payments/page` | VOLUNTEER+ (`PaymentController.java:82`) | query `page`, `size`, `search`, `method`, `sort` (see [Paged list](#paged-list-get-apipaymentspage)) | `{content, page, size, totalElements, totalPages}` |
| GET | `/api/payments/paid-months` | VOLUNTEER+ (`PaymentController.java:97`) | query `months` (see [Paid months](#paid-months-get-apipaymentspaid-months)) | `{ "<memberId>": ["2026-09", "2026-10"], ... }` |
| GET | `/api/payments/{id}` | VOLUNTEER+ (`PaymentController.java:104`) | path `id` > 0 | `Payment`, or 404 with empty body |
| GET | `/api/payments/member/{memberId}` | VOLUNTEER+ (`PaymentController.java:114`) | path `memberId` > 0 | `List<Payment>`, or 404 with empty body if the member does not exist, or is archived and the caller is not an ADMIN |
| POST | `/api/payments` | STAFF+ (`PaymentController.java:123`) | `RecordPaymentRequest` JSON, `@Valid` | 200 + saved `Payment` |
| GET | `/api/payments/export` | STAFF+ (`PaymentController.java:132`) | none | `text/csv; charset=UTF-8` attachment `payments.csv`, UTF-8 with a byte order mark |

Roles and hierarchy: [../authentication.md](../authentication.md).

### Paged list (GET /api/payments/page)
`getPaymentPage` (`PaymentController.java:82`) reads one page; the screens that need a table of payments use it instead of downloading the whole list.

| Query param | Default | Rule |
|-------------|---------|------|
| `page` | `0` | zero-based, at least 0 |
| `size` | `25` | 1 to 100 |
| `search` | none | optional, trimmed, at most 100 characters; empty means no search. A case-insensitive substring of the member's name (`person.name`), or a receipt number: `R-000141`, `r-000141`, `000141` or `141` (the receipt number is the payment id padded to 6 digits, so these digits are the id: an exact match, not a substring). `%` and `_` are plain text |
| `method` | none | optional, one of the 7 `PaymentMethod` codes (case-insensitive); empty means any |
| `sort` | `paymentDate,desc` | `field[,direction]`: field one of `paymentDate`, `period`, `amount`, `member` (the member's name, case-insensitive); direction `asc` (when omitted) or `desc`. Ties always fall back to the newest id first, so a page never repeats or skips a row |

Response: `{"content": [ ...same objects as GET /api/payments... ], "page": 0, "size": 25, "totalElements": 142, "totalPages": 6}`. `page` and `size` echo the request; an empty result is `content: []`, `totalElements: 0`, `totalPages: 0`; a page past the end is `content: []` with the real totals. Archived members' payments are listed for every role, exactly like `GET /api/payments`: a non-ADMIN caller gets the member without email and phone. One count statement and one page statement (member, person and household are fetched with the page; `PaymentPagingIntegrationTest` checks the statement count); on a first page that is not full Spring skips the count.

### Paid months (GET /api/payments/paid-months)
`getPaidMonths` (`PaymentController.java:97`): for the Members, Overview, Messages and Households screens and the member picker, which only need to know who paid which month.

- `months`: default `12`, 1 to 36. The window is the current month and the `months - 1` before it, both ends inclusive (12 in October 2026 is November 2025 to October 2026).
- Response: a JSON object keyed by member id (a string, as JSON requires), each value the member's distinct `yyyy-MM` billing periods in the window, oldest first: `{"4": ["2026-08", "2026-10"], "7": ["2026-10"]}`. A member with no payment in the window is absent. A month paid twice (two payments for one period) appears once.
- One grouped statement (`PaymentJpaRepository.findPaidMonthsBetween`), not one per member. Archived members are included, like `GET /api/payments`; the response holds ids and months, no contact details.

### Request body (POST)
`RecordPaymentRequest` (`src/main/java/io/github/membertracker/infrastructure/dto/RecordPaymentRequest.java:15-29`):
- `memberId`: required, positive. The member is loaded by id server-side; the client never sends a member object.
- `amount`: required, >= 0.01.
- `paymentMethod`: required, enum code (`CASH`, `BANK_TRANSFER`, `CREDIT_CARD`, `DEBIT_CARD`, `MOBILE_PAYMENT`, `ONLINE_PAYMENT`, `CHECK`, `domain/enumeration/PaymentMethod.java:13-19`); an unknown code is a 400.
- `period`: optional `YearMonth`, JSON `"2026-10"`; absent means the current month. Any month up to and including the current one, at most 10 years back.
- `paymentDate`: optional date, JSON `"2024-03-10"`, `@PastOrPresent` (a future date is a 400 with a field error); absent means today.
- `notes`: optional, max 500 characters.

Example: `{"memberId": 1, "amount": 50.0, "paymentMethod": "CASH", "period": "2026-10"}`, shared with the frontend test as `src/test/resources/contracts/record-payment-request.json` (`PaymentContractTest`). The back-dated variant with `paymentDate` is `src/test/resources/contracts/record-payment-request-backfill.json` (`{"memberId": 1, "amount": 50.0, "paymentMethod": "CHECK", "period": "2024-03", "paymentDate": "2024-03-10"}`), also posted by `PaymentContractTest`.

### CSV columns
`id,memberId,memberName,amount,paymentDate,period,method` (`PaymentController.java:136`). Member name goes through `CsvUtils.escapeCsv` (`PaymentController.java:142`), which prefixes formula-looking text with `'` and quotes fields containing `,` `"` or newlines (`src/main/java/io/github/membertracker/utils/CsvUtils.java:26-39`). The CSV is built in memory and returned as a plain `byte[]` response (`CsvUtils.attachment`) with a UTF-8 byte order mark so Excel reads non-Latin names correctly; `notes` is not exported.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetPaymentPageUseCase` | paged list | `src/main/java/io/github/membertracker/usecase/GetPaymentPageUseCase.java` (`PaymentRepository.findPage`, `PaymentPageQuery` and `PageResult` in `domain/model`) |
| `GetPaidMonthsUseCase` | paid months | `src/main/java/io/github/membertracker/usecase/GetPaidMonthsUseCase.java` (`PaymentRepository.findPaidMonthsBetween`) |
| `GetAllPaymentsUseCase` | list, export | `src/main/java/io/github/membertracker/usecase/GetAllPaymentsUseCase.java:21` (`findAll`) |
| `GetPaymentByIdUseCase` | get by id | `src/main/java/io/github/membertracker/usecase/GetPaymentByIdUseCase.java:22` |
| `GetMemberByIdUseCase` | member payments (existence check) | `PaymentController.java:118` |
| `GetPaymentsByMemberUseCase` | member payments | `src/main/java/io/github/membertracker/usecase/GetPaymentsByMemberUseCase.java:23` |
| `RecordPaymentUseCase` | POST | `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:32` |
| `RecordPaymentRequest` | POST body | see Request body |
| `CsvUtils` | export | see above |

### Record flow
`RecordPaymentUseCase.invoke(memberId, amount, paymentMethod, period, paymentDate, notes)` (`RecordPaymentUseCase.java:32-65`):
0. Load the member with `memberRepository.findById`, else `MemberDomainException.memberNotFound`; an INACTIVE member is rejected with `MemberDomainException.memberInactive` (`MEMBER_008`); build the `Payment`, period defaulting to `YearMonth.now()` and payment date to today unless `paymentDate` was sent
1. `validateAmount`, `validatePeriod`, `validatePaymentDate`
2. Reject if the member already has a payment for that period (`RecordPaymentUseCase.java:50`)
3. `markAsProcessed` fills `paymentDate` with today only if still missing
4. `member.recordPayment` sets `lastPaymentDate` to the later of its current value and the payment date (it never moves backwards); resets `consecutiveMonthsMissed` only when the period is the current month (`src/main/java/io/github/membertracker/domain/model/Member.java:46-57`)
5. Save member, then payment (`RecordPaymentUseCase.java:59-60`)

### Domain rules
- Amount > 0 (`Payment.java:45-49`), plus bean validation min 0.01 (`Payment.java:26`)
- Period: any month up to and including the current one is accepted (history can be entered); a future month is rejected (`PAYMENT_007`); a month more than 10 years back is rejected as a probable typo (`Payment.MAX_YEARS_BACK`, `PAYMENT_002`, message "too far back")
- Payment date: not in the future (`PAYMENT_008`; also `@PastOrPresent` on the request, which gives a field error)
- Member must be active, else `MEMBER_008` "Member '<name>' is inactive. Reactivate the member before recording a payment."
- One payment per member per period (`RecordPaymentUseCase.java:50`)

## Errors
All RFC 7807 ([../architecture.md](../architecture.md)); handler `src/main/java/io/github/membertracker/infrastructure/handler/GlobalExceptionHandler.java`.

| Status | Source | Cause |
|--------|--------|-------|
| 400 | `MethodArgumentNotValidException`, `GlobalExceptionHandler.java:52` | POST body fails bean validation (`errors[]` with field + message) |
| 400 | Spring's unreadable-body handling (`GlobalExceptionHandler.java:41`) | malformed JSON, unknown `paymentMethod` code, bad `period` format |
| 400 | `ConstraintViolationException`, `GlobalExceptionHandler.java:75` | `id` / `memberId` not positive; `page` below 0, `size` outside 1 to 100, `months` outside 1 to 36 (`errors[].field` names the parameter) |
| 400 | `MethodArgumentTypeMismatchException`, Spring's own handling | `page`, `size` or `months` is not a number |
| 400 | `PaymentDomainException.invalidPageQuery`, `GlobalExceptionHandler.java:67` | `search` over 100 characters, unknown `method`, unknown `sort` field or direction; `code` `PAYMENT_009`, `errors[]` with the field (`search`, `method`, `sort`); the rejected value is not echoed |
| 400 | `DomainException`, `GlobalExceptionHandler.java:67` | amount, period (future / more than 10 years back), payment date in the future, inactive member or duplicate-period rule; `code` property: `PAYMENT_001`, `PAYMENT_002`, `PAYMENT_007`, `PAYMENT_008`, `MEMBER_004`, `MEMBER_006` for an unknown `memberId`, `MEMBER_008` for an inactive member (`src/main/java/io/github/membertracker/domain/exception/PaymentDomainException.java:12-18`, `src/main/java/io/github/membertracker/domain/exception/MemberDomainException.java:13`, `:15`) |
| 403 | `AccessDeniedException`, `GlobalExceptionHandler.java:86` | role too low |
| 401 | `GlobalExceptionHandler.java:93` | not authenticated |
| 404 | `ResponseEntity.notFound()` in controller (`PaymentController.java:111`, `:120`) | unknown payment or member; empty body, not a ProblemDetail |
| 500 | `GlobalExceptionHandler.java:101` | anything else, including a failure while building the CSV |

## Side effects
- POST updates the member row (`lastPaymentDate`, maybe `consecutiveMonthsMissed`) before saving the payment, in two `save` calls (`RecordPaymentUseCase.java:59-60`).
- A successful POST adds a `PAYMENT_RECORDED` entry to the activity log (`RecordPaymentUseCase.java:61-64`); the CSV export adds `PAYMENTS_EXPORTED` with the row count (`PaymentController.java:148`). Both are best effort, no emails or phones in them: [activity.md](activity.md).
- No emails sent.

## Gotchas
- `GET /api/payments` still returns every payment and is the source of the CSV export; the page and paid-months endpoints are the way for screens to avoid it. The three literal paths (`/page`, `/paid-months`, `/export`) win over `/{id}`.
- Searching `141` finds the payment with id 141 (R-000141) and every member whose name contains `141`; a receipt search is an exact id, never a substring of other ids.
- Payments cannot be deleted or voided yet; a void feature would need an audit trail.
- `ProcessMemberPaymentUseCase` (never called) was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`; `RecordPaymentUseCase` is the only record path.
- A payment for an inactive member is rejected: reactivate the member first. Reactivating resets the missed-months counter (`Member.activate`).
- The missed-months counter is raised by `Member.markMissedFor` (`Member.java:64`) through the monthly job in [payment-reminder-scheduler.md](payment-reminder-scheduler.md) (audit C3).
