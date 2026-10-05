# Payments

Monthly membership dues: staff record who paid what for which month (and, for history, the day it was paid), everyone with access can review the history and open a receipt; staff export a CSV.

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| View payment history and figures | VOLUNTEER | `/payments` (`frontend/src/router/index.js:24-29`), `GET /api/payments/page`, `GET /api/payments/summary` (`src/main/java/io/github/membertracker/infrastructure/PaymentController.java`); the plain `GET /api/payments` list is no longer read by any screen |
| Read one page of payments, or which months each member paid | VOLUNTEER | `GET /api/payments/page`, `GET /api/payments/paid-months` (`PaymentController.java:82-102`); used by Payments (the page) and by the year strips on Members, Overview, Messages, Households and the member picker (the months), so no screen downloads every payment: [payment-controller.md](payment-controller.md#paged-list-get-apipaymentspage) |
| View one payment, or one member's payments | VOLUNTEER | `GET /api/payments/{id}` (`PaymentController.java:104-112`), `GET /api/payments/member/{memberId}` (`PaymentController.java:114-121`); the member page lists one member's payments, and the Record payment dialog reads them to prefill the amount; nothing reads one payment by id |
| Record a payment | STAFF | "Record payment" button and dialog on `/payments` (`frontend/src/views/PaymentsView.vue:4`), `POST /api/payments` (`PaymentController.java:123-130`) |
| Open a receipt, download it as PDF | VOLUNTEER | the receipt number ("R-000012") on a history row |
| Export payments to CSV | STAFF | "Export CSV" button (`PaymentsView.vue:4`), `GET /api/payments/export` (`PaymentController.java:132-151`) |
| Delete or void a payment | nobody | not available |

A VOLUNTEER sees the page without the "Record payment" and "Export CSV" buttons; STAFF and ADMIN see both (`isStaff`, `frontend/src/stores/authStore.js:39`). The server answers 403 to a VOLUNTEER's write or export.

## How it works
### View payment history
1. Open Payments. The page loads one page of the history (`GET /api/payments/page`, 25 rows, newest first), the three figures (`GET /api/payments/summary`) and, for STAFF and above, the members (for the dialog's Member select), at once. Page, size, search, method and sort are in the URL (`?page=&size=&search=&method=&sort=`).
2. Three figures come from the server (`GET /api/payments/summary`, one aggregate query) and are shown as dollars: "This month" (payments whose billing month, the `period`, is the current month), "All time" and "Average payment". "This month" follows the billing month, the same as the Overview, so the two agree ([dashboard.md](dashboard.md)).
3. The history is a ruled table from `md` up and a stacked list below it. Columns: Paid on, Member, Month covered ("Oct 2026"), Method (label), Amount (right aligned), Receipt. Newest first by paid-on date, then id by default; the Paid on, Member, Month and Amount headers sort it on the server (`sort`). Filters: search by member name or receipt number (after 300 ms without typing) and a Method select; "Clear filters" appears when one is set; a count line reads "32 payments" or "3 of 32 payments", and the pager (25 rows a page, 10, 25, 50 or 100) pages the result.
4. Empty states: "No payments yet. Record the first one." (with a button for STAFF and above); "No payments match these filters." with "Clear filters"; on a load failure a banner with "Try again".

### Record a payment
1. STAFF opens the "Record payment" dialog and picks: Member (active members only, sorted by name), Month covered (default and maximum the current month), Paid on (default and maximum today; hint "Change this when you enter an older payment."), Amount (at least 0.01), Payment method (default Cash) and optional Notes (500 characters, counter) (`PaymentsView.vue:114-140`).
2. The browser sends `memberId`, `amount`, `paymentMethod`, `period` and, when set, `paymentDate` and `notes`, built only by `buildPaymentRequest` (`frontend/src/utils/paymentPayload.js:13-23`). Field rules: [payment-controller.md](payment-controller.md#request-body-post).
3. The server loads the member by id and refuses a member whose status is not MEMBER, builds the payment (period defaults to the current month, payment date to today), validates amount, period and payment date, then refuses a second payment for the same member and month (`src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:32-53`).
4. The member's `lastPaymentDate` becomes the later of its current value and the payment date, so a back-dated payment never moves it backwards. The missed-months counter is reset to 0 only when the period is the current month (`src/main/java/io/github/membertracker/domain/model/Member.java:46-59`, `Member.java:90-96`).
5. Member and payment are saved (`RecordPaymentUseCase.java:59-60`).
6. Success: the dialog closes, the form resets (month and date back to the defaults), the history reloads and a toast "Payment recorded" names the member, month and amount (`PaymentsView.vue:339-357`).
7. Failure keeps the dialog open: a field error from the server (`error.fieldErrors`) shows under its field; a 400 without a field (duplicate month: "Member 'X' already has a payment recorded for period 2026-10"; inactive member `MEMBER_008`: "Member 'X' is inactive. Reactivate the member before recording a payment."; a month more than 10 years back) shows in a banner at the top of the dialog, plus an error toast "Could not record payment" (no toast for 403). Error codes: [payment-controller.md](payment-controller.md#errors).

### Open or print a receipt
1. Click the receipt number on a row (`PaymentsView.vue:358-361`).
2. The dialog "Receipt R-000012" shows the receipt number, member, month covered, paid on, method, notes (when present) and the amount.
3. The receipt number is `R-` plus the payment id padded to 6 digits, computed in the browser (`receiptNumber`, `paymentHistory.js:34-37`); it is not stored.
4. "Download PDF" saves `receipt-R-000012.pdf` (letter, portrait). The PDF library is loaded on the first click (`PaymentsView.vue:362-380`). "Close" closes the dialog.

### Export payments to CSV
1. Click "Export CSV". The server returns every payment as a plain UTF-8 CSV download with a byte order mark, so Excel shows Amharic names correctly (`PaymentController.java:90-109`).
2. The browser saves `payments_<today>.csv` (`PaymentsView.vue:389-394`).
3. Columns: `id,memberId,memberName,amount,paymentDate,period,method` (`PaymentController.java:94`). Notes are not exported. Failure: toast "Export failed".

## Rules
- Amount must be at least 0.01 (`src/main/java/io/github/membertracker/domain/model/Payment.java:48-52`; bean validation `src/main/java/io/github/membertracker/infrastructure/dto/RecordPaymentRequest.java:20-22`). No other minimum or maximum.
- Period: any month up to and including the current one; a future month is rejected; a month more than 10 years back is rejected as a probable typo (`Payment.java:54-68`, limit `Payment.java:15`). Example: in October 2026, anything from October 2016 to October 2026 is accepted.
- One payment per member per month (`RecordPaymentUseCase.java:50-53`).
- Method is one of 7 codes: `CASH`, `BANK_TRANSFER`, `CREDIT_CARD`, `DEBIT_CARD`, `MOBILE_PAYMENT`, `ONLINE_PAYMENT`, `CHECK` (`src/main/java/io/github/membertracker/domain/enumeration/PaymentMethod.java:15-21`; form list `frontend/src/utils/paymentPayload.js:1-9`). An unknown code is a 400.
- The payment date defaults to today; the client may send an earlier `paymentDate` (back-dating history), never a future one (`Payment.java:70-74`, `RecordPaymentRequest.java:30-32`). The "Paid on" field defaults to today and the dialog's `max` is today.
- Notes are limited to 500 characters (`RecordPaymentRequest.java:34-35`).
- Payments cannot be edited, deleted or voided. The DELETE endpoint was removed; payments are financial records.
- The member must exist (unknown id is a 400) and must have status MEMBER: INACTIVE, DECEASED, TRANSFERRED and ARCHIVED are a 400 (`MEMBER_008`, message unchanged: "Member 'X' is inactive. Reactivate the member before recording a payment."). The dialog only lists active members, so this is reachable only through a stale list or the API.
- Exports (this CSV, the members CSV and the selected-members CSV) need STAFF.
- "Behind" and "paid up" apply to MEMBER-status members only: any other status's stored counter is stale, so the screens show a dash.

## Known issues
- No way to correct a mistaken payment (wrong amount, wrong member). A void-with-audit-trail feature is future work: [payment-controller.md](payment-controller.md#gotchas), audit C7 in [../functionality-audit.md](../functionality-audit.md).
- The missed-months counter is raised only by the monthly job through `Member.markMissedFor` (`Member.java:66-73`, [payment-reminder-scheduler.md](payment-reminder-scheduler.md)).
- The unused `paymentStore` was removed in `chore(ui): remove dead frontend code`; the view calls `api.js` directly ([payments-view.md](payments-view.md#collaborators)).
- The history is paged, searched, filtered and sorted by the server (no date range filter yet); the figures come from `GET /api/payments/summary`. `GET /api/payments` is still there but no screen reads it; the CSV export builds from the same repository call.
- Amounts are `Double` and shown with a `$` sign (`Payment.java:30`, `frontend/src/utils/index.js:42`).
- The receipt and the history read the nested member name and show "Unknown" if it is missing.
- Member save and payment save are two separate calls in one use case (`RecordPaymentUseCase.java:59-60`); the use case class has no `@Transactional` (only `ChangePasswordUseCase` does), so a failure between the two saves could leave the member updated without a payment.

## Related
- [payment-controller.md](payment-controller.md) (API, errors, record flow)
- [payments-view.md](payments-view.md) (screen state and actions)
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md)
- [members-view.md](members-view.md), [dashboard-view.md](dashboard-view.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
- [../functionality-audit.md](../functionality-audit.md)
