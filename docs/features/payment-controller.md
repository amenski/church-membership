# PaymentController

`src/main/java/io/github/membertracker/infrastructure/PaymentController.java`

Membership-dues API: list, look up, record and export payments. Roles per endpoint below.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/payments` | VOLUNTEER+ (`PaymentController.java:56`) | none | `List<Payment>` |
| GET | `/api/payments/{id}` | VOLUNTEER+ (`PaymentController.java:63`) | path `id` > 0 | `Payment`, or 404 with empty body |
| GET | `/api/payments/member/{memberId}` | VOLUNTEER+ (`PaymentController.java:72`) | path `memberId` > 0 | `List<Payment>`, or 404 with empty body if the member does not exist |
| POST | `/api/payments` | STAFF+ (`PaymentController.java:81`) | `RecordPaymentRequest` JSON, `@Valid` | 200 + saved `Payment` |
| GET | `/api/payments/export` | VOLUNTEER+ (`PaymentController.java:86`) | none | `text/csv; charset=UTF-8` attachment `payments.csv`, UTF-8 with a byte order mark |

Roles and hierarchy: [../authentication.md](../authentication.md).

### Request body (POST)
`RecordPaymentRequest` (`src/main/java/io/github/membertracker/infrastructure/dto/RecordPaymentRequest.java:15-29`):
- `memberId`: required, positive. The member is loaded by id server-side; the client never sends a member object.
- `amount`: required, >= 0.01.
- `paymentMethod`: required, enum code (`CASH`, `BANK_TRANSFER`, `CREDIT_CARD`, `DEBIT_CARD`, `MOBILE_PAYMENT`, `ONLINE_PAYMENT`, `CHECK`, `domain/enumeration/PaymentMethod.java:13-19`); an unknown code is a 400.
- `period`: optional `YearMonth`, JSON `"2026-10"`; absent means the current month.
- `notes`: optional, max 500 characters.

Example: `{"memberId": 1, "amount": 50.0, "paymentMethod": "CASH", "period": "2026-10"}`, shared with the frontend test as `src/test/resources/contracts/record-payment-request.json` (`PaymentContractTest`). `paymentDate` is not accepted; it is set to today.

### CSV columns
`id,memberId,memberName,amount,paymentDate,period,method` (`PaymentController.java:90`). Member name goes through `CsvUtils.escapeCsv` (`PaymentController.java:95`), which prefixes formula-looking text with `'` and quotes fields containing `,` `"` or newlines (`src/main/java/io/github/membertracker/utils/CsvUtils.java:26-39`). The CSV is built in memory and returned as a plain `byte[]` response (`CsvUtils.attachment`) with a UTF-8 byte order mark so Excel reads non-Latin names correctly; `notes` is not exported.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllPaymentsUseCase` | list, export | `src/main/java/io/github/membertracker/usecase/GetAllPaymentsUseCase.java:21` (`findAll`) |
| `GetPaymentByIdUseCase` | get by id | `src/main/java/io/github/membertracker/usecase/GetPaymentByIdUseCase.java:22` |
| `GetMemberByIdUseCase` | member payments (existence check) | `PaymentController.java:75` |
| `GetPaymentsByMemberUseCase` | member payments | `src/main/java/io/github/membertracker/usecase/GetPaymentsByMemberUseCase.java:23` |
| `RecordPaymentUseCase` | POST | `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:22` |
| `RecordPaymentRequest` | POST body | see Request body |
| `CsvUtils` | export | see above |

### Record flow
`RecordPaymentUseCase.invoke(memberId, amount, paymentMethod, period, notes)` (`RecordPaymentUseCase.java:22-44`):
0. Load the member with `memberRepository.findById`, else `MemberDomainException.memberNotFound` (`RecordPaymentUseCase.java:24-25`); build the `Payment`, period defaulting to `YearMonth.now()` (`RecordPaymentUseCase.java:27-28`)
1. `validateAmount` then `validatePeriod` (`RecordPaymentUseCase.java:30-31`)
2. Reject if the member already has a payment for that period (`RecordPaymentUseCase.java:33`)
3. Default `paymentDate` to today (`Payment.java:102-106`)
4. `member.recordPayment` sets `lastPaymentDate`; resets `consecutiveMonthsMissed` only when the period is the current month (`src/main/java/io/github/membertracker/domain/model/Member.java:47-58`)
5. Save member, then payment (`RecordPaymentUseCase.java:42-43`)

### Domain rules
- Amount > 0 (`Payment.java:46-50`), plus bean validation min 0.01 (`Payment.java:27`)
- Period not in the future, not older than 3 months before the current month (`Payment.java:52-66`)
- One payment per member per period (`RecordPaymentUseCase.java:33`)

## Errors
All RFC 7807 ([../architecture.md](../architecture.md)); handler `src/main/java/io/github/membertracker/infrastructure/handler/GlobalExceptionHandler.java`.

| Status | Source | Cause |
|--------|--------|-------|
| 400 | `MethodArgumentNotValidException`, `GlobalExceptionHandler.java:52` | POST body fails bean validation (`errors[]` with field + message) |
| 400 | Spring's unreadable-body handling (`GlobalExceptionHandler.java:41`) | malformed JSON, unknown `paymentMethod` code, bad `period` format |
| 400 | `ConstraintViolationException`, `GlobalExceptionHandler.java:75` | `id` / `memberId` not positive |
| 400 | `DomainException`, `GlobalExceptionHandler.java:67` | amount, period (future / over 3 months old) or duplicate-period rule; `code` property: `PAYMENT_001`, `PAYMENT_002`, `PAYMENT_007`, `MEMBER_004`, `MEMBER_006` for an unknown `memberId` (`src/main/java/io/github/membertracker/domain/exception/PaymentDomainException.java:12-18`, `src/main/java/io/github/membertracker/domain/exception/MemberDomainException.java:13`, `:15`) |
| 403 | `AccessDeniedException`, `GlobalExceptionHandler.java:86` | role too low |
| 401 | `GlobalExceptionHandler.java:93` | not authenticated |
| 404 | `ResponseEntity.notFound()` in controller (`PaymentController.java:68`, `:77`) | unknown payment or member; empty body, not a ProblemDetail |
| 500 | `GlobalExceptionHandler.java:101` | anything else, including a failure while building the CSV |

## Side effects
- POST updates the member row (`lastPaymentDate`, maybe `consecutiveMonthsMissed`) before saving the payment, in two `save` calls (`RecordPaymentUseCase.java:42-43`).
- No emails sent.

## Gotchas
- Payments cannot be deleted or voided yet; a void feature would need an audit trail.
- `ProcessMemberPaymentUseCase` (never called) was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`; `RecordPaymentUseCase` is the only record path.
- Recording a payment does not check that the member is active (`RecordPaymentUseCase.java:22-44` never reads `active`).
- `Member.markPaymentMissed` (`Member.java:60`) still has no caller; the missed-months counter is raised by `Member.markMissedFor` (`Member.java:69`) through the monthly job in [payment-reminder-scheduler.md](payment-reminder-scheduler.md) (audit C3).
