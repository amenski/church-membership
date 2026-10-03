# PaymentController

`src/main/java/io/github/membertracker/infrastructure/PaymentController.java`

Membership-dues API: list, look up, record and export payments, plus a delete endpoint that does not delete. Roles per endpoint below.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/payments` | VOLUNTEER+ (`PaymentController.java:56`) | none | `List<Payment>` |
| GET | `/api/payments/{id}` | VOLUNTEER+ (`PaymentController.java:63`) | path `id` > 0 | `Payment`, or 404 with empty body |
| GET | `/api/payments/member/{memberId}` | VOLUNTEER+ (`PaymentController.java:72`) | path `memberId` > 0 | `List<Payment>`, or 404 with empty body if the member does not exist |
| POST | `/api/payments` | STAFF+ (`PaymentController.java:81`) | `Payment` JSON, `@Valid` | 200 + saved `Payment` |
| DELETE | `/api/payments/{id}` | ADMIN (`PaymentController.java:88`) | path `id` > 0 | 200 empty, or 404 empty (see Gotchas) |
| GET | `/api/payments/export` | VOLUNTEER+ (`PaymentController.java:100`) | none | `text/csv` attachment `payments.csv` |

Roles and hierarchy: [../authentication.md](../authentication.md).

### Request body (POST)
Bound straight onto the domain model, no DTO: `member` (object, required), `period` (`YearMonth`, required), `paymentDate` (not in future), `amount` (>= 0.01, required), `paymentMethod` (required), `notes`. Constraints: `src/main/java/io/github/membertracker/domain/model/Payment.java:17-33`. Methods: `domain/enumeration/PaymentMethod.java:13-18`.

### CSV columns
`id,memberId,memberName,amount,paymentDate,period,method` (`PaymentController.java:111`). Member name goes through `CsvUtils.escapeCsv` (`PaymentController.java:118`), which prefixes formula-looking text with `'` and quotes fields containing `,` `"` or newlines (`src/main/java/io/github/membertracker/utils/CsvUtils.java:26-39`). Whole list is read first, then streamed; `notes` is not exported.

## Collaborators
| Dependency | Used by | Ref |
|------------|---------|-----|
| `GetAllPaymentsUseCase` | list, export | `src/main/java/io/github/membertracker/usecase/GetAllPaymentsUseCase.java:21` (`findAll`) |
| `GetPaymentByIdUseCase` | get by id, delete | `src/main/java/io/github/membertracker/usecase/GetPaymentByIdUseCase.java:22` |
| `GetMemberByIdUseCase` | member payments (existence check) | `PaymentController.java:75` |
| `GetPaymentsByMemberUseCase` | member payments | `src/main/java/io/github/membertracker/usecase/GetPaymentsByMemberUseCase.java:23` |
| `RecordPaymentUseCase` | POST | `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:19` |
| `CsvUtils` | export | see above |

`ProcessMemberPaymentUseCase` is not used by this controller or any other entry point: only declared as a bean (`src/main/java/io/github/membertracker/infrastructure/config/UseCaseConfig.java:145`) and unit-tested.

### Record flow
`RecordPaymentUseCase.invoke` (`RecordPaymentUseCase.java:19-36`):
1. `validateAmount` then `validatePeriod` (`RecordPaymentUseCase.java:22-23`)
2. Reject if the member already has a payment for that period (`RecordPaymentUseCase.java:25`)
3. Default `paymentDate` to today (`Payment.java:102-106`)
4. `member.recordPayment` sets `lastPaymentDate`; resets `consecutiveMonthsMissed` only when the period is the current month (`src/main/java/io/github/membertracker/domain/model/Member.java:46-57`)
5. Save member, then payment (`RecordPaymentUseCase.java:34-35`)

### Domain rules
- Amount > 0 (`Payment.java:46-50`), plus bean validation min 0.01 (`Payment.java:27`)
- Period not in the future, not older than 3 months before the current month (`Payment.java:52-66`)
- One payment per member per period (`RecordPaymentUseCase.java:25`)

## Errors
All RFC 7807 ([../architecture.md](../architecture.md)); handler `src/main/java/io/github/membertracker/infrastructure/handler/GlobalExceptionHandler.java`.

| Status | Source | Cause |
|--------|--------|-------|
| 400 | `MethodArgumentNotValidException`, `GlobalExceptionHandler.java:52` | POST body fails bean validation (`errors[]` with field + message) |
| 400 | `ConstraintViolationException`, `GlobalExceptionHandler.java:75` | `id` / `memberId` not positive |
| 400 | `DomainException`, `GlobalExceptionHandler.java:67` | amount, period (future / over 3 months old) or duplicate-period rule; `code` property: `PAYMENT_001`, `PAYMENT_002`, `PAYMENT_007`, `MEMBER_004` (`src/main/java/io/github/membertracker/domain/exception/PaymentDomainException.java:12-18`, `src/main/java/io/github/membertracker/domain/exception/MemberDomainException.java:13`) |
| 403 | `AccessDeniedException`, `GlobalExceptionHandler.java:86` | role too low |
| 401 | `GlobalExceptionHandler.java:93` | not authenticated |
| 404 | `ResponseEntity.notFound()` in controller (`PaymentController.java:68`, `:77`, `:95`) | unknown payment or member; empty body, not a ProblemDetail |
| 500 | `GlobalExceptionHandler.java:101` | anything else, including a failure mid-CSV-stream (`PaymentController.java:127`) |

## Side effects
- POST updates the member row (`lastPaymentDate`, maybe `consecutiveMonthsMissed`) before saving the payment, in two `save` calls (`RecordPaymentUseCase.java:34-35`).
- No emails sent.

## Gotchas
- DELETE deletes nothing: it checks the payment exists and returns 200 (`PaymentController.java:91-93`). Source comment admits it (`PaymentController.java:92`) (audit C7).
- `ProcessMemberPaymentUseCase` is dead code from the API's point of view (see Collaborators).
- `member` in the POST body is a full `Member` object, not an id (`Payment.java:17-18`); a body with only `memberId` fails with 400 "Member is required".
