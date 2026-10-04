# Dashboard

`frontend/src/views/Dashboard.vue`

Home screen (nav label "Overview"), top to bottom: a slim facts strip of up to four `StatTile`s (`slim`): Collected in <month> (`stats.monthlyRevenue`), Paid up (N of M members), Behind on dues (N members, X months unpaid, the sum of the members' missed counts) and Reminders (failed deliveries summed from the `deliverySummary` of REMINDER messages, with a Review link to `/communications`; the tile is left out when that call fails); then two columns from `lg` up. Left, the **dues ledger** ("Dues by member"): every active member who is behind or has not paid the current month, most behind first (then by name), each row the name as stored (Amharic or Latin), the compact `YearStrip` (from 1400px up, the `ledger` size: 28px squares under a header of month names, titled "Dues by month", with a legend top right and a footer row "Members who paid" counting dues-paying members with a payment in each month; it scrolls sideways inside the card if it does not fit) and "N months behind" (or "Due this month" for someone not behind yet but unpaid this month), under a "Nov to Oct" column header. Right, **Call this week**: the three members furthest behind with months behind, a Call `tel:` link when the member has a phone, and Send reminder (STAFF+) which links to `/communications` (it no longer sends an email from here). Under it, Recent payments. Below both, "Collected by month" and Recent activity side by side. Each list has a plain empty state. Below `sm` a ledger row wraps: name and months behind on one line, the strip under it. Styled with Tailwind utilities and the small components `PageHead`, `SectionTitle`, `StatTile`, `YearStrip`, `RuledList`/`RuledRow`, `StatusLabel`, `TextButton`, `EmptyNote` and `AlertBanner` (see [../design.md](../design.md)). Route `/dashboard` (`frontend/src/router/index.js:12-17`), minimum role VOLUNTEER; `homePath` for VOLUNTEER and above (`frontend/src/stores/authStore.js:41`). Guards: [../authentication.md](../authentication.md).

## Top bar
The app's top bar (`frontend/src/App.vue`, not this view) shows, from `lg` up and for VOLUNTEER and above, today's date ("Sunday, 4 October 2026", local, re-read on each page change) and a "Search members" box (32px, a visually hidden label, Enter submits). Submitting goes to `/members?search=<text>` (trimmed; empty goes to `/members`) and clears the box; the Members screen reads `?search=` when it opens. Both are hidden below `lg`, where the tab bar and each page's own search cover it. Layout notes: [../design.md](../design.md#layout).

## State
No Pinia store for data; Options API local `data()` (`frontend/src/views/Dashboard.vue:112-127`), with `authStore` / `appStore` from `setup()` (`:103-111`).
- `stats` -> `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}`, initial zeros
- `recentPayments`, `activities` -> arrays from the dashboard endpoints; `members` -> `GET /members` (all members, with phone, join date and `consecutiveMonthsMissed`); `failedReminders` -> number or `null`; `wide` -> true from 1400px (`matchMedia`, off where it does not exist); `paidByMember` -> `Map` of member id to paid `yyyy-MM` periods from `GET /payments` (`paidMonthsByMember`), `null` when that call failed (the strips then show a muted dash and only members with a missed count are listed); `today`
- `loaded`, `loadError` -> `loaded` turns true once the first load finished (a "Loading overview..." line shows before that, so a zero never flashes); `loadError` shows an alert and the error also goes to `console.error`
- Computed: `activeCount` (from `stats.activeMembers`), `behindMembers` (members with status MEMBER and `consecutiveMonthsMissed` above 0, longest first; this replaced the overdue-members call), `callList` (the first three), `ledgerRows` (MEMBER status and either behind or, when the payments loaded, no payment for the current month; most behind first, then by name), `paidCount` = `activeCount` minus `behindMembers.length`, never below 0. The month squares follow the rule in [../design.md](../design.md#year-strip) and `frontend/src/utils/yearStrip.js`, the same as the Members screen.

## Actions
- `loadData()` -> `Promise.all` of 4 calls (stats, recent payments, members, activities), then assigns all state, then loads "collected by month", the paid months and the failed reminders (each fails on its own without hiding the rest); runs on `created`. One failure of the first four aborts the whole assignment (nothing updates).
- Send reminder is a link to `/communications`; the Overview no longer sends mail itself.
- `formatDate(date)` (`:172`) -> `new Date(date).toLocaleDateString()`
- `formatMoney(amount)` (shared, `frontend/src/utils/index.js:42`, exposed from `setup()` at `:108`) -> US dollars with two decimals; the same helper formats the Payments screen

## Collaborators
| api.js method | Request | Backend |
|---------------|---------|---------|
| `getDashboardStats` (`frontend/src/services/api.js:472`) | GET `/dashboard/stats` | [dashboard-controller.md](dashboard-controller.md) |
| `getRecentPayments` (`:522`) | GET `/dashboard/recent-payments` | same |
| `getMembers` | GET `/members` (VOLUNTEER+) | [member-controller.md](member-controller.md) |
| `getPayments` | GET `/payments` (VOLUNTEER+), for the year strips | [payment-controller.md](payment-controller.md) |
| `getCommunications` | GET `/communications` (VOLUNTEER+), for the failed-reminders tile | [communication-controller.md](communication-controller.md) |
| `getRecentActivities` (`:530`) | GET `/dashboard/recent-activities` | same |

Methods return `response.data` (`frontend/src/services/api.js:285-288`).

## Errors
- 401: api.js interceptor refreshes the token, retries, else clears auth and redirects (`frontend/src/services/api.js:105-171`; see [../authentication.md](../authentication.md)).
- Load failures: an alert at the top ("The overview did not load. Reload the page, or sign in again if it keeps happening.") and a console log. The server no longer answers 200 with zeros when it fails: a failure is a 500 and reaches this banner.
- A role-denied visit to another page lands here (VOLUNTEER and above) with an "Access denied" warning toast raised by the router guard (`frontend/src/router/index.js:108-118`); the URL has no query parameter and this view reads nothing.

## Side effects
None: the screen only reads. Send reminder and Call are plain links.

## Gotchas
- The dashboard endpoints no longer catch exceptions (see [dashboard-controller.md](dashboard-controller.md#errors)), so the error alert also covers a server failure.
- `formatDate` here still does `new Date(date).toLocaleDateString()`: a `YYYY-MM-DD` date parses as UTC and can show a day early west of UTC, unlike `formatDate` in `frontend/src/utils/index.js` used on Members and Payments.
- Activity rows have no icon; the type is not shown. The backend only emits `payment` and `communication`.
- The "Member" column reads the nested `payment.member?.name` (`:57`); payments carry no `memberId` (`src/main/java/io/github/membertracker/domain/model/Payment.java:18`), but the nested `member` is always loaded (`payment.member_id` is NOT NULL, `src/main/resources/db/sql/001.schema-creation.sql:23`; mapped at `src/main/java/io/github/membertracker/infrastructure/persistence/repository/PaymentDbRepository.java:77`), so the `Unknown` fallback is not expected to show.
