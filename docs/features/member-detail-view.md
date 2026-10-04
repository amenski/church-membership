# Member detail

`frontend/src/views/MemberDetailView.vue`

One member on one page: their dues by month, latest payments, contact details and household. Route `/members/:id`, sign-in required, minimum role VOLUNTEER (`frontend/src/router/index.js`, breadcrumb title "Member"). The names in the Members list and the Overview ledger link here. The rail and the phone tab bar keep Members lit, because `/members/5` is under `/members`.

## Who can do what
| Task | Minimum role | Endpoint |
|------|--------------|----------|
| See the page | VOLUNTEER | `GET /api/members/{id}`, `GET /api/payments/member/{id}`, `GET /api/households/{id}` |
| Send message (link to `/communications`) | VOLUNTEER | none |
| Record payment (link to `/payments?memberId=<id>`) | STAFF, member status MEMBER | none |
| Edit details | STAFF, not archived | `PUT /api/members/{id}` |
| Archive | ADMIN | `DELETE /api/members/{id}` |
| See an archived member | ADMIN | `GET /api/members/{id}` answers 404 to everyone else |

No endpoint was added.

## Screen
- Back link "Members", then the header: name, "Member since Jan 2023", the status label (`StatusLabel`), and from `lg` up the actions Send message, Record payment, Archive (Archive is an outline in clay).
- Dues by month: the dues label (N months behind, or Paid up, only for status MEMBER), the owed sentence (`owedSummary` in `utils/dues.js`: "Owes July, August and September 2026. October is due now.", or "Paid up..."), and the strip. From `lg` the strip is `YearStrip size="detail" :months="24"`: two rows of 12 squares (44x36px, month name above each, a caption such as "Nov 2024 to Oct 2025" over each row) with a legend. Below `lg` it is the 12-month `large` strip, with Call and Record payment (44px) under it, as in MemberDetailPhone. A member who does not owe dues (Inactive, Deceased, Transferred, Archived) gets "Dues are not tracked while this member is inactive." instead of a sentence, and nothing red or amber.
- Payments: the latest 6 (`sortPayments`): receipt number (opens the receipt dialog, `ReceiptDialog`), month, paid on, method, amount; a stacked list below `md`. The footer says "Showing the latest 6 of 14 payments." with "All payments", which opens `/payments?search=<member name>`.
- Contact: phone (a `tel:` link), email, joined, status, last paid ("Never"), with Call (from `lg`; on a phone Call is in the dues card) and Edit details (STAFF+).
- Household (omitted without a household): the household name, its members with a status label (for a member who owes dues: "Paid up" or "N months behind"; the others show their status; this member is marked "(this member)", the others link to their own page), its people without a membership ("No membership"), and "Open household" (`/households`).
- Phone only, at the bottom: Send message and, for an ADMIN, Archive.
- Edit details and Archive reuse `MemberFormDialog` and `MemberArchiveDialog`, the same dialogs as the Members list. After Edit the page reloads; after Archive it goes back to `/members` (toast "Member archived").

## Not found, archived, errors
- 404 (or 400 for a bad id) from `GET /members/{id}`: "Member not found. This member does not exist, or is not on the list any more." with "Back to Members". That is also what a VOLUNTEER or STAFF sees for an archived member.
- An ADMIN who opens an archived member sees the page read-only: a note says it is archived and to restore from Members, Archived; Edit, Archive, Send message and Record payment are hidden.
- Any other failure of the member: banner with "Try again". If only the payments fail, the dues card and payments card say so (no strip is drawn, because it would show wrong squares). If only the household fails, the card is left out.

## Data and rules
- The strip uses the same rule as every other strip (`frontend/src/utils/yearStrip.js`): `stripCells({ ..., count: 24 })`, `paidMonths` from the member's payments, `monthsMissed` from `consecutiveMonthsMissed`. `stripMonths(currentMonth, count = 12)` and `stripCells` take the count; the cells carry `short` (the month name).
- The household members' dues come from `api.getMembers()`, called only when the household has someone besides this member; if it fails they show their status only. `GET /api/households/{id}` returns members as `{id, name, status}` and people as `{id, name, birthDate, memberStatus}`; a person with a null `memberStatus` has no membership.
- `api.getMember(id)` and `api.getPaymentsByMember(id)` are new wrappers in `frontend/src/services/api.js`.

## History card: not built
The activity log is ADMIN only and `GET /api/activity-log` takes only `limit` (max 200): there is no per-member filter. Filtering the latest 200 entries in the browser would silently miss older ones, so the History card is left out until the endpoint accepts `entityType` and `entityId`.

## Known issues
- The page has no Amharic name above the Latin one: the data has one name field (see design-gaps).
- The Household card links to the Households list; there is no route for one household, its detail is a dialog there.
- Names in the Archived list of Members do not link here.
