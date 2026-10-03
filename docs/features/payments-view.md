# PaymentsView

`frontend/src/views/PaymentsView.vue` (store: `frontend/src/stores/paymentStore.js`)

Payments page: record a payment (STAFF+ only), revenue cards, payments table, receipt modal with PDF download, CSV export. Route `/payments`, minimum role VOLUNTEER (`frontend/src/router/index.js:24-28`; guard `frontend/src/router/index.js:101-104`, see [../authentication.md](../authentication.md)).

## Endpoints
Calls made through `frontend/src/services/api.js`:

| Method | Path | api.js | Used by |
|--------|------|--------|---------|
| GET | `/payments` | `frontend/src/services/api.js:382` | `loadData` |
| GET | `/members` | `getMembers` | `loadData` |
| POST | `/payments` (STAFF+) | `frontend/src/services/api.js:390` | `submitPayment`, body from `buildPaymentRequest` |
| GET | `/payments/export` (blob) | `frontend/src/services/api.js:394` | `exportPayments` |

Backend: [payment-controller.md](payment-controller.md).

## State
Local component `data()` (`frontend/src/views/PaymentsView.vue:204-217`):
- `members`, `payments`: arrays, forced to `[]` if the response is not an array
- `analytics`: `totalRevenue`, `monthlyRevenue` (payments dated in the current calendar month), `averagePayment`; strings from `toFixed(2)`, or number `0` when no payments
- `paymentMethods`: the 6 backend codes with labels (`PAYMENT_METHODS`, `frontend/src/utils/paymentPayload.js:1-8`)
- `newPayment`: form model `{ memberId, amount, paymentMethod: 'CASH', period }`; `period` defaults to the current local month as `YYYY-MM` (`PaymentsView.vue:185-194`)
- `selectedPayment`: payment shown in the receipt modal

`setup()` exposes `appStore` and `authStore` (`PaymentsView.vue:198-203`). No `isLoading` / `error`.

## Actions
- `loadData` -> `Promise.all` of members + payments, then `calculateAnalytics`; on error empties both lists and shows an error toast (`PaymentsView.vue:222-241`)
- `calculateAnalytics` -> sums client-side from `payments` (`PaymentsView.vue:243-279`)
- `submitPayment` -> `api.createPayment(buildPaymentRequest(newPayment))`, reload, reset form, open receipt for the response; on error shows a toast (`PaymentsView.vue:280-293`)
- `buildPaymentRequest(form)` -> `{ memberId: Number, amount: Number, paymentMethod, period, notes? }`, notes only when non-empty (`frontend/src/utils/paymentPayload.js:11-20`)
- `receiptNumber(payment)` -> `R-` + payment id zero-padded to 6 digits, derived because the backend has no receipt number (`PaymentsView.vue:297-299`)
- `notifyError(title, error, fallback)` -> error toast with `error.message` (the server `detail`) (`PaymentsView.vue:300-307`)
- `generateReceipt(payment)` -> sets `selectedPayment`, opens the Bootstrap modal (`PaymentsView.vue:308-311`)
- `downloadReceipt` -> PDF of the modal body (`PaymentsView.vue:312-323`). `html2pdf.js` is dynamically imported on first click (`PaymentsView.vue:321`), letter portrait, file `receipt-<receiptNumber>.pdf`
- `exportPayments` -> `api.exportPayments()`, saves blob as `payments_<YYYY-MM-DD>.csv` via `downloadBlob` (`PaymentsView.vue:324-338`, `frontend/src/utils/index.js:92`). On failure shows an error toast through the app store

## Collaborators
- `frontend/src/services/api.js` (default import, `PaymentsView.vue:178`)
- `useAppStore().addNotification` for error toasts (`PaymentsView.vue:180`)
- `useAuthStore().isStaff` shows the record form only to STAFF and above (`PaymentsView.vue:11`; VOLUNTEERs can view, the server answers 403 to STAFF-only writes)
- `buildPaymentRequest`, `PAYMENT_METHODS` (`frontend/src/utils/paymentPayload.js`), tested against the shared contract fixture `src/test/resources/contracts/record-payment-request.json` (`frontend/src/__tests__/utils/paymentPayload.test.js`)
- `bootstrap` Modal (`PaymentsView.vue:179`)
- `usePaymentStore`: not imported by this view. Store exposes `payments`, `isLoading`, `error`, filters, pagination, `loadPayments`, `createPayment` etc. (`frontend/src/stores/paymentStore.js:222-251`); only re-exported (`frontend/src/stores/index.js:4`), no consumer found.

## Errors
- Load / create / export errors: error toast (title "Could not load payments", "Payment failed", "Export failed") with the server `detail`, else a fixed fallback (`PaymentsView.vue:240`, `:291`, `:300-307`, `:331-337`)
- A VOLUNTEER never sees the form, so the 403 on POST is not reachable from the UI

## Side effects
- Triggers a file download for CSV and PDF
- Creating a payment re-fetches members and payments

## Gotchas
- The table and receipt read the nested `payment.member?.name` (falls back to "Unknown" if the member is missing) (`PaymentsView.vue:91`, `:136`).
- The store's `filters.paymentMethod` comment lists `card` (`paymentStore.js:16`), which is not a backend method value.
