# PaymentsView

`frontend/src/views/PaymentsView.vue` (store: `frontend/src/stores/paymentStore.js`)

Payments page: record a payment, revenue cards, payments table, receipt modal with PDF download, CSV export. Route `/payments`, minimum role VOLUNTEER (`frontend/src/router/index.js:24-28`; guard `frontend/src/router/index.js:101-104`, see [../authentication.md](../authentication.md)).

## Endpoints
Calls made through `frontend/src/services/api.js`:

| Method | Path | api.js | Used by |
|--------|------|--------|---------|
| GET | `/payments` | `frontend/src/services/api.js:382` | `loadData` |
| GET | `/members` | `getMembers` | `loadData` |
| POST | `/payments` (STAFF+) | `frontend/src/services/api.js:390` | `submitPayment` |
| GET | `/payments/export` (blob) | `frontend/src/services/api.js:394` | `exportPayments` |

Backend: [payment-controller.md](payment-controller.md).

## State
Local component `data()` (`frontend/src/views/PaymentsView.vue:186-201`):
- `members`, `payments`: arrays, forced to `[]` if the response is not an array
- `analytics`: `totalRevenue`, `monthlyRevenue` (payments dated in the current calendar month), `averagePayment`; strings from `toFixed(2)`, or number `0` when no payments
- `newPayment`: form model `{ memberId, amount, paymentMethod: 'CASH' }`
- `selectedPayment`: payment shown in the receipt modal

No `isLoading` / `error`; failures are only `console.error` except the export.

## Actions
- `loadData` -> `Promise.all` of members + payments, then `calculateAnalytics`; on error empties both lists (`PaymentsView.vue:207-226`)
- `calculateAnalytics` -> sums client-side from `payments` (`PaymentsView.vue:227-263`)
- `submitPayment` -> `api.createPayment(newPayment)`, reload, reset form, open receipt for the response (`PaymentsView.vue:264-280`)
- `generateReceipt(payment)` -> sets `selectedPayment`, opens the Bootstrap modal (`PaymentsView.vue:288-291`)
- `downloadReceipt` -> PDF of the modal body (`PaymentsView.vue:292-303`). `html2pdf.js` is dynamically imported on first click (`PaymentsView.vue:301`), letter portrait, file `receipt-<receiptNumber>.pdf`
- `exportPayments` -> `api.exportPayments()`, saves blob as `payments_<YYYY-MM-DD>.csv` via `downloadBlob` (`PaymentsView.vue:304-318`, `frontend/src/utils/index.js:92`). On failure shows an error toast through the app store (`PaymentsView.vue:311-316`)

## Collaborators
- `frontend/src/services/api.js` (default import, `PaymentsView.vue:174`)
- `useAppStore().addNotification` for the export error toast (`PaymentsView.vue:176`)
- `bootstrap` Modal (`PaymentsView.vue:175`)
- `usePaymentStore`: not imported by this view. Store exposes `payments`, `isLoading`, `error`, filters, pagination, `loadPayments`, `createPayment` etc. (`frontend/src/stores/paymentStore.js:222-251`); only re-exported (`frontend/src/stores/index.js:4`), no consumer found.

## Errors
- Load / create errors: logged only, no user message (`PaymentsView.vue:222`, `:278`)
- Export errors: toast "Export failed" with server `detail` or "Could not export CSV" (`PaymentsView.vue:311-316`)
- A 403 on POST for VOLUNTEER comes from the backend; the form is always shown (`PaymentsView.vue:14`)

## Side effects
- Triggers a file download for CSV and PDF
- Creating a payment re-fetches members and payments

## Gotchas
- Form posts `{ memberId, amount, paymentMethod }` (`PaymentsView.vue:195-199`), but the backend `Payment` requires a `member` object and a `period` (`src/main/java/io/github/membertracker/domain/model/Payment.java:17-21`), so the create call is rejected with 400 as written. Not listed in the audit.
- Table and receipt read `payment.memberId` and `payment.receiptNumber` (`PaymentsView.vue:87`, `:90`); the backend `Payment` has `member` and no receipt number (`Payment.java:13-33`), so member shows "Unknown" and the receipt number is blank.
- Form offers 3 of the 6 backend methods (`PaymentsView.vue:30-32`).
- The store's `filters.paymentMethod` comment lists `card` (`paymentStore.js:16`), which is not a backend method value.
