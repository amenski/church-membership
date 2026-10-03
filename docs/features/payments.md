# Payments

Monthly membership dues: staff record who paid what for which month (and, for history, the day it was paid), everyone with access can review the history and print a receipt; staff export a CSV.

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| View payment history and totals | VOLUNTEER | `/payments` (`frontend/src/router/index.js:25-29`), `GET /api/payments` (`src/main/java/io/github/membertracker/infrastructure/PaymentController.java:56`) |
| View one payment, or one member's payments | VOLUNTEER | `GET /api/payments/{id}` (`PaymentController.java:63`), `GET /api/payments/member/{memberId}` (`PaymentController.java:72`); API only, no screen uses them |
| Record a payment | STAFF | form on `/payments` (`frontend/src/views/PaymentsView.vue:11`), `POST /api/payments` (`PaymentController.java:81`) |
| View / print a receipt | VOLUNTEER | Receipt button on a table row (`PaymentsView.vue:96`) |
| Export payments to CSV | STAFF | Export CSV button (`PaymentsView.vue:5`), `GET /api/payments/export` (`PaymentController.java:86`) |
| Delete or void a payment | nobody | not available |

A VOLUNTEER sees the page without the record form. STAFF and ADMIN see it (`isStaff`, `frontend/src/stores/authStore.js:41`).

## How it works
### View payment history
1. Open Payments. The page loads all members and all payments at once (`PaymentsView.vue:225-228`).
2. Three cards are computed in the browser from that list: total revenue, this calendar month (by payment date), average payment (`PaymentsView.vue:243-279`).
3. The table shows date, member name, amount, method code, receipt number and a Receipt button (`PaymentsView.vue:89-100`). It is not sorted, paged or filterable on screen.
4. On failure an error toast "Could not load payments" appears and the table is empty (`PaymentsView.vue:236-241`).

### Record a payment
1. STAFF picks a member, enters an amount, the payment month and the method (`PaymentsView.vue:15-38`). Defaults: method Cash, month = current month (`PaymentsView.vue:189-194`). There is no notes field, although the API accepts one.
2. The browser sends `memberId`, `amount`, `paymentMethod`, `period` (`frontend/src/utils/paymentPayload.js:11-20`); the API also accepts an optional `paymentDate` (the day it was paid, not in the future). Field rules: [payment-controller.md](payment-controller.md#request-body-post).
3. The server loads the member by id, builds the payment (period defaults to the current month) and checks that the member is active, then amount, period, payment date and duplicates (`src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:24-36`).
4. The payment date is the `paymentDate` sent, or today.
5. The member's `lastPaymentDate` is set to the later of its current value and the payment date (it never moves backwards). The missed-months counter is reset to 0 only when the period is the current month; a back-dated payment leaves the counter alone (`src/main/java/io/github/membertracker/domain/model/Member.java:46-57`, `Member.java:88-94`).
6. Member and payment are saved (`RecordPaymentUseCase.java:42-43`).
7. Success: the table and cards reload, the form resets, and the receipt modal opens for the new payment (`PaymentsView.vue:282-288`).
8. Failure (a 400 such as "Member 'X' already has a payment recorded for period 2026-10", or an inactive member): a toast "Payment failed" with the server's message; the form keeps what was typed (`PaymentsView.vue:289-292`). Error codes: [payment-controller.md](payment-controller.md#errors).

### View or print a receipt
1. Click Receipt on a row, or record a payment (it opens automatically) (`PaymentsView.vue:308-311`).
2. The modal shows receipt number, date, member name, amount, method and blank "Received By" / "Member Signature" lines (`PaymentsView.vue:116-162`). It does not show the paid month.
3. The receipt number is `R-` plus the payment id padded to 6 digits, computed in the browser; it is not stored (`PaymentsView.vue:297-299`).
4. Download PDF saves `receipt-R-000012.pdf` (letter, portrait). The PDF library is loaded on the first click (`PaymentsView.vue:312-323`).

### Export payments to CSV
1. Click Export CSV. The server returns every payment as a plain UTF-8 CSV download with a byte order mark, so Excel shows Amharic names correctly (`PaymentController.java:86-102`).
2. The browser saves `payments_<today>.csv` (`PaymentsView.vue:324-328`).
3. Columns: `id,memberId,memberName,amount,paymentDate,period,method` (`PaymentController.java:90`). Notes are not exported. Failure: toast "Export failed" (`PaymentsView.vue:329-337`).

## Rules
- Amount must be above 0 (`Payment.java:45-49`; bean validation `Payment.java:26`, `src/main/java/io/github/membertracker/infrastructure/dto/RecordPaymentRequest.java:20-21`). No other minimum or maximum.
- Period: any month up to and including the current one; a future month is rejected; a month more than 10 years back is rejected as a probable typo (`Payment.java`). Example: in October 2026, anything from October 2016 to October 2026 is accepted.
- One payment per member per month (`RecordPaymentUseCase.java:33-36`).
- Method is one of 7 codes: `CASH`, `BANK_TRANSFER`, `CREDIT_CARD`, `DEBIT_CARD`, `MOBILE_PAYMENT`, `ONLINE_PAYMENT`, `CHECK` (`src/main/java/io/github/membertracker/domain/enumeration/PaymentMethod.java:15-21`; form list `paymentPayload.js:1-9`). An unknown code is a 400.
- The payment date defaults to today; the client may send an earlier `paymentDate` (back-dating history), never a future one.
- Notes, if sent through the API, are limited to 500 characters (`RecordPaymentRequest.java:29-30`).
- Payments cannot be edited, deleted or voided. The DELETE endpoint was removed; payments are financial records.
- The member must exist; an unknown member id is a 400. The member must be active: an inactive member is a 400 (`MEMBER_008`, "Reactivate the member before recording a payment").
- Exports (this CSV, the members CSV and the selected-members CSV) need STAFF.

## Known issues
- No way to correct a mistaken payment (wrong amount, wrong member). A void-with-audit-trail feature is future work: [payment-controller.md](payment-controller.md#gotchas), audit C7 in [../functionality-audit.md](../functionality-audit.md).
- Historic payments can be entered through the API (`period` and `paymentDate`), but the payment form has no paid-on date field yet.
- The missed-months counter is raised only by the monthly job through `Member.markMissedFor` (`Member.java:64`); the unused `Member.markPaymentMissed` was removed in `chore: remove unused domain methods` ([payment-reminder-scheduler.md](payment-reminder-scheduler.md)).
- `ProcessMemberPaymentUseCase` (never called by the API) was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`; it can be recovered from git history.
- `frontend/src/stores/paymentStore.js` is unused: the view calls `api.js` directly; the store is only re-exported (`frontend/src/stores/index.js:4`) ([payments-view.md](payments-view.md#collaborators)).
- The page lists and sums every payment in the browser (no paging, no date filter); revenue cards and totals ignore the period and use the payment date (`PaymentsView.vue:225-279`).
- Amounts are `Double` and shown with a `$` sign (`Payment.java:27`, `PaymentsView.vue:92`).
- The receipt reads the nested member name and shows "Unknown" if missing (`PaymentsView.vue:91`, `:136`).
- The Export CSV button is still shown to volunteers although the endpoint now needs STAFF (they get a 403 toast).
- Member save and payment save are two separate calls in one use case (`RecordPaymentUseCase.java:42-43`); the use case class has no `@Transactional` (only `ChangePasswordUseCase` does), so a failure between the two saves could leave the member updated without a payment.

## Related
- [payment-controller.md](payment-controller.md) (API, errors, record flow)
- [payments-view.md](payments-view.md) (screen state and actions)
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md)
- [members-view.md](members-view.md), [dashboard-view.md](dashboard-view.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
- [../functionality-audit.md](../functionality-audit.md)
