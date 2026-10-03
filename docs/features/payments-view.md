# PaymentsView

`frontend/src/views/PaymentsView.vue` (store: `frontend/src/stores/paymentStore.js`, not used by this view)

Payments page, built on Tailwind and the shared components: a "Record payment" dialog (STAFF and above), three quiet figures, a filterable history (ruled table from `md` up, stacked list below), a receipt dialog with PDF download, CSV export. Route `/payments`, minimum role VOLUNTEER (`frontend/src/router/index.js:24-29`; guard `frontend/src/router/index.js:102-103`, see [../authentication.md](../authentication.md)).

## What the user sees
- Page head "Payments", lead "Record what members paid and see the history." STAFF and above also get two buttons: "Export CSV" (secondary) and "Record payment" (primary). A VOLUNTEER gets a read-only screen: no buttons, no dialog (the server answers 403 to their writes and exports).
- Figures row (only when there are payments): "This month", "All time", "Average payment", computed on the client by `paymentsSummary` from the loaded payments and shown with `formatMoney` ("$1,520.00"). "This month" sums payments whose `period` is the current month, not payments entered this month, so a back-dated payment never counts here.
- Filters (only when there are payments): search by member name, a Method select ("All methods" plus the 7 methods), "Clear filters" when one is set, and a count line ("32 payments", or "3 of 32 payments" when filtered).
- History, newest first (paid on descending, then id descending, `sortPayments`): Paid on, Member, Month covered ("Oct 2026", `periodLabel`), Method (label), Amount (right aligned, tabular), Receipt (a text button "R-000012" that opens the receipt dialog).
- Empty states: no payments "No payments yet. Record the first one." (with a "Record payment" button for STAFF and above); filters match nothing "No payments match these filters." with "Clear filters"; a load failure shows a banner "The payments did not load. Check your connection and try again." with "Try again".

## Record payment dialog
`BaseModal` md, STAFF and above only. Fields: Member (select of ACTIVE members only, sorted by name; with no active members the dialog shows an `EmptyNote` and the submit button is disabled), Month covered (`type="month"`, default and `max` the current month), Paid on (`type="date"`, default and `max` today, hint "Change this when you enter an older payment."), Amount (`min` 0.01, step 0.01), Payment method (7 methods, default Cash), Notes (optional, 500 characters, counter).
- The body is built only by `buildPaymentRequest(form)` -> `{ memberId: Number, amount: Number, paymentMethod, period, paymentDate?, notes? }`; `paymentDate` and `notes` are left out when blank (`frontend/src/utils/paymentPayload.js:13-23`).
- Client checks (`validateForm`): member chosen, month not in the future, date not in the future, amount at least 0.01, notes at most 500.
- Server errors (`showSaveError`): `error.fieldErrors` `[{ field, message }]` go under their fields; a 400 without a field goes into an `AlertBanner` at the top of the dialog and an error toast "Could not record payment" (no toast for 403, the shared API handler already shows one). The dialog stays open on failure.
- Success closes the dialog, resets the form (month and date back to the defaults), reloads the history and shows the toast "Payment recorded" ("Jane Smith, Mar 2024: $50.00").

## Receipt dialog
`BaseModal` sm titled "Receipt R-000012". Shows Receipt, Member, Month covered, Paid on, Method, Notes (only when present) and the Amount, inside a plain element (`receiptContent`) that html2pdf captures: only token hex colours, no tinted or blended colours. "Download PDF" imports `html2pdf.js` on first click, letter portrait, file `receipt-R-000012.pdf` (`PaymentsView.vue:362-380`); "Close" closes it. A PDF failure shows the toast "Could not create the PDF".

## Endpoints
Calls made through `frontend/src/services/api.js`:

| Method | Path | api.js | Used by |
|--------|------|--------|---------|
| GET | `/payments` | `frontend/src/services/api.js:382` | `loadData` |
| GET | `/members` (STAFF and above only; a VOLUNTEER does not load it) | `frontend/src/services/api.js:350` | `loadData`, for the Member select |
| POST | `/payments` (STAFF+) | `frontend/src/services/api.js:390` | `recordPayment` |
| GET | `/payments/export` (blob, STAFF+) | `frontend/src/services/api.js:394` | `exportPayments` |

Backend: [payment-controller.md](payment-controller.md). Rules the server enforces: any past month up to 10 years back is valid (a future month is a 400); `paymentDate` is optional, must not be in the future, and a back-dated payment never moves the member's last-payment date backwards; an INACTIVE member is refused with `MEMBER_008` ("Member '<name>' is inactive. Reactivate the member before recording a payment."); a second payment for the same member and month is refused ("Member '<name>' already has a payment recorded for period <YYYY-MM>"). The request shapes are tested against the shared fixtures `src/test/resources/contracts/record-payment-request.json` and `record-payment-request-backfill.json` (`frontend/src/__tests__/utils/paymentPayload.test.js`).

## State and helpers
Local component `data()` (`frontend/src/views/PaymentsView.vue:235-252`): `members`, `payments` (forced to `[]` when the response is not an array), `loaded`, `loadError`, `filters` (`search`, `method`), `recordOpen`, `form`, `formError`, `formErrors`, `saving`, `today`, `selectedPayment`, `receiptOpen`, `downloading`. `setup()` exposes the stores and the formatting helpers (`PaymentsView.vue:217-234`).

Pure helpers in `frontend/src/utils/paymentHistory.js` (tested in `frontend/src/__tests__/utils/paymentHistory.test.js`): `paymentsSummary(payments, currentPeriod)`, `periodLabel('2026-10')` -> "Oct 2026", `receiptNumber(payment)` -> `R-` plus the id padded to 6 digits (derived: the backend has no receipt number), `methodLabel`, `sortPayments`, `filterPayments`. `formatMoney` lives in `frontend/src/utils/index.js:94` and is shared with the Overview. `PAYMENT_METHODS` is in `frontend/src/utils/paymentPayload.js:1-9`.

## Collaborators
- `frontend/src/services/api.js` (default import)
- `useAppStore().addNotification` for toasts, `useAuthStore().isStaff` for the buttons and the dialog
- Components: `PageHead`, `AlertBanner`, `BaseButton`, `BaseInput`, `BaseSelect`, `BaseTextarea`, `BaseModal`, `EmptyNote`, `TextButton`
- `usePaymentStore`: not imported by this view; only re-exported (`frontend/src/stores/index.js:4`), no consumer found.

## Side effects
- Triggers a file download for CSV and PDF
- Recording a payment re-fetches members and payments

## Gotchas
- The table and receipt read the nested `payment.member?.name` (falls back to "Unknown").
- The month input is a native `type="month"`; browsers without it show a text box, and the server still validates `YYYY-MM`.
- The store's `filters.paymentMethod` comment lists `card` (`paymentStore.js:16`), which is not a backend method value.
