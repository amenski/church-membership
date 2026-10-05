# PaymentsView

`frontend/src/views/PaymentsView.vue` (no Pinia store for data)

Payments page, built on Tailwind and the shared components: a "Record payment" dialog (STAFF and above), three slim figures, the 12-month "Collected by month" chart, a filterable history (a bordered table card from `lg`, a ruled table from `md`, stacked list below), a receipt dialog with PDF download, CSV export. Route `/payments`, minimum role VOLUNTEER (`frontend/src/router/index.js:24-29`; guard `frontend/src/router/index.js:108-109`, see [../authentication.md](../authentication.md)).

## What the user sees
- Page head "Payments", lead "Record what members paid and see the history." From `lg` the head is the full-width band (title left, the buttons right, no lead) and the content sits in a padded area under it (see [../design.md](../design.md#layout)). STAFF and above also get two buttons: "Export CSV" (secondary) and "Record payment" (primary). A VOLUNTEER gets a read-only screen: no buttons, no dialog (the server answers 403 to their writes and exports).
- Figures row (only when there are payments, `StatTile slim` like the Overview's strip): "This month", "All time", "Average payment", computed on the client by `paymentsSummary` from the loaded payments and shown with `formatMoney` ("$1,520.00"). "This month" sums payments whose `period` is the current month, not payments entered this month, so a back-dated payment never counts here.
- "Collected by month" (only when there are payments), a bordered card (`CARD`, the same as the Overview ledger had) between the figures and the filters: the `CollectedChart` component draws twelve vertical columns, oldest to newest, scaled to the largest month (150px for the tallest), the amount above each column and the month (Nov, Dec ...) below, from `GET /api/dashboard/collected-by-month`. The head reads "November 2025 to October 2026, by the month the dues are for" with the twelve-month total on the right. The current month is a hatched, teal-outlined column, with a line under the chart saying it is still in progress. Amounts above the columns are whole dollars ("$1,250") from `md` up and abbreviated below it ("$1.3k", so 12 columns fit 390px); the columns and labels are `aria-hidden` and a visually hidden table lists every month with its exact amount. It loads itself; a failure shows "The monthly amounts did not load." with "Try again", and no payments in 12 months shows its own empty note. Recording a payment reloads it. It is no longer on the Overview.
- Filters (only when there are payments): search by member name, a Method select ("All methods" plus the 7 methods), "Clear filters" when one is set, and a count line ("32 payments", or "3 of 32 payments" when filtered). From `lg` they are one 32px row with the labels visually hidden: search on the left, "Clear filters" after it, Method on the right; the count moves to the table card's footer (the line stays for screen readers).
- History, newest first (paid on descending, then id descending, `sortPayments`). From `lg` it is a bordered card with a grey header row and a footer line ("32 payments" left, "Newest first" right), columns Receipt (a text button "R-000012" that opens the receipt dialog), Paid on, Member, Month, Method, Amount (right aligned); it scrolls sideways inside the card when narrow. From `md` to `lg` (and below as a stacked list) the earlier layout stays: Paid on, Member, Month covered ("Oct 2026", `periodLabel`), Method (label), Amount (right aligned, tabular), Receipt (a text button "R-000012" that opens the receipt dialog).
- Empty states: no payments "No payments yet. Record the first one." (with a "Record payment" button for STAFF and above); filters match nothing "No payments match these filters." with "Clear filters"; a load failure shows a banner "The payments did not load. Check your connection and try again." with "Try again".

## Record payment dialog
`BaseModal` md (a full-screen sheet below `lg`, see below), STAFF and above only. Fields: Member (select of ACTIVE members only, sorted by name; with no active members the dialog shows an `EmptyNote` and the submit button is disabled), Month covered (`type="month"`, default and `max` the current month), Paid on (`type="date"`, default and `max` today, hint "Change this when you enter an older payment."), Amount (`min` 0.01, step 0.01), Payment method (7 methods, default Cash), Notes (optional, 500 characters, counter).
- The body is built only by `buildPaymentRequest(form)` -> `{ memberId: Number, amount: Number, paymentMethod, period, paymentDate?, notes? }`; `paymentDate` and `notes` are left out when blank (`frontend/src/utils/paymentPayload.js:13-23`).
- Client checks (`validateForm`): member chosen, month not in the future, date not in the future, amount at least 0.01, notes at most 500.
- Server errors (`showSaveError`): `error.fieldErrors` `[{ field, message }]` go under their fields; a 400 without a field goes into an `AlertBanner` at the top of the dialog and an error toast "Could not record payment" (no toast for 403, the shared API handler already shows one). The dialog stays open on failure.
- Success closes the dialog, resets the form (month and date back to the defaults), reloads the history and shows the toast "Payment recorded" ("Jane Smith, Mar 2024: $50.00").

## Record payment on a phone
Below `lg` the same `BaseModal` is a full-screen sheet (`sheet` prop, see [../design.md](../design.md#layout)): Back and the title in a dark top bar, the form scrolling, and a pinned bottom bar with "Record payment" (48px, primary) over "Cancel". Controls are 48px high and 16px text, so iOS does not zoom. The desktop dialog is unchanged.
- Once a member is chosen a card under the Member select shows the name, the large year strip and one sentence from `owedSummary` (`frontend/src/utils/dues.js`), built from the same `stripCells` rule as the Members list: "Owes July, August and September 2026. October is due now." (months the server counts as missed, then the current month if unpaid); "Paid up to last month. October is due now."; or "Paid up. Nothing is owed for October 2026." when nothing is owed. The strip uses the payments the view already loaded.
- Choosing a member sets Month covered to the oldest month not paid (the oldest missed month, else the current month) and shows the hint "July 2026, the oldest month not paid." while the field still holds that month. Both happen below `lg` only; on desktop the month stays the current one and there is no hint. Months older than the strip's 12-month window are not counted.
- Unlike the mockup the Member select stays (the Payments tab opens the sheet with no member); the deep link below fills it.

## Deep link
`/payments?search=<name>` (the member page's "All payments") starts with that text in the member search filter.

`/payments?memberId=<id>` (the Members phone card's Record payment link) opens the Record payment dialog with that member chosen. After the first load `openForQueryMember` removes the parameter from the URL (`router.replace`), so a reload or closing the dialog does not reopen it, then, for STAFF and above only, opens the dialog and sets `form.memberId` when the id is an active member (a member who does not owe dues leaves the select on "Choose a member"). A VOLUNTEER, or a failed load, just gets the plain screen. The rest of the screen is unchanged by the redesign: the dark-teal rail only changes the frame around it.

## Receipt dialog
`ReceiptDialog` (`frontend/src/components/ReceiptDialog.vue`, also used by [the member page](member-detail-view.md)): a `BaseModal` sm titled "Receipt R-000012". Shows Receipt, Member, Month covered, Paid on, Method, Notes (only when present) and the Amount, inside a plain element (`receiptContent`) that html2pdf captures: only token hex colours, no tinted or blended colours. "Download PDF" imports `html2pdf.js` on first click, letter portrait, file `receipt-R-000012.pdf` (`PaymentsView.vue:362-380`); "Close" closes it. A PDF failure shows the toast "Could not create the PDF".

## Endpoints
Calls made through `frontend/src/services/api.js`:

| Method | Path | api.js | Used by |
|--------|------|--------|---------|
| GET | `/payments` | `frontend/src/services/api.js:366` | `loadData` |
| GET | `/members` (STAFF and above only; a VOLUNTEER does not load it) | `frontend/src/services/api.js:338` | `loadData`, for the Member select |
| POST | `/payments` (STAFF+) | `frontend/src/services/api.js:370` | `recordPayment` |
| GET | `/payments/export` (blob, STAFF+) | `frontend/src/services/api.js:374` | `exportPayments` |

Backend: [payment-controller.md](payment-controller.md). Rules the server enforces: any past month up to 10 years back is valid (a future month is a 400); `paymentDate` is optional, must not be in the future, and a back-dated payment never moves the member's last-payment date backwards; an INACTIVE member is refused with `MEMBER_008` ("Member '<name>' is inactive. Reactivate the member before recording a payment."); a second payment for the same member and month is refused ("Member '<name>' already has a payment recorded for period <YYYY-MM>"). The request shapes are tested against the shared fixtures `src/test/resources/contracts/record-payment-request.json` and `record-payment-request-backfill.json` (`frontend/src/__tests__/utils/paymentPayload.test.js`).

## State and helpers
Local component `data()` (`frontend/src/views/PaymentsView.vue:235-252`): `members`, `payments` (forced to `[]` when the response is not an array), `loaded`, `loadError`, `filters` (`search`, `method`), `recordOpen`, `form`, `formError`, `formErrors`, `saving`, `today`, `selectedPayment`, `receiptOpen`. `setup()` exposes the stores and the formatting helpers (`PaymentsView.vue:217-234`).

Pure helpers in `frontend/src/utils/paymentHistory.js` (tested in `frontend/src/__tests__/utils/paymentHistory.test.js`): `paymentsSummary(payments, currentPeriod)`, `periodLabel('2026-10')` -> "Oct 2026", `receiptNumber(payment)` -> `R-` plus the id padded to 6 digits (derived: the backend has no receipt number), `methodLabel`, `sortPayments`, `filterPayments`. `formatMoney` lives in `frontend/src/utils/index.js:42` and is shared with the Overview. `PAYMENT_METHODS` is in `frontend/src/utils/paymentPayload.js:1-9`.

## Collaborators
- `frontend/src/services/api.js` (default import)
- `useAppStore().addNotification` for toasts, `useAuthStore().isStaff` for the buttons and the dialog
- Components: `PageHead`, `CollectedChart`, `StatTile`, `AlertBanner`, `BaseButton`, `BaseInput`, `BaseSelect`, `BaseTextarea`, `BaseModal`, `EmptyNote`, `TextButton`, `YearStrip`
- The unused `paymentStore` was removed in `chore(ui): remove dead frontend code`; the view calls `api.js` directly.

## Side effects
- Triggers a file download for CSV and PDF
- Recording a payment re-fetches members and payments, and the chart

## Gotchas
- The table and receipt read the nested `payment.member?.name` (falls back to "Unknown").
- The month input is a native `type="month"`; browsers without it show a text box, and the server still validates `YYYY-MM`.
