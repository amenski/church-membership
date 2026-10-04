# Dashboard

`frontend/src/views/Dashboard.vue`

Home screen (nav label "Overview"): a "Who needs a call" hero (the sentence "N of M active members are paid up", the dues meter, the list of active members who are behind, each with a Send reminder link), a quiet row with this month's payments and active members, then ruled lists of recent payments and recent activity, each with a plain empty state. Styled with Tailwind utilities and the small components `PageHead`, `SectionTitle`, `RuledList`/`RuledRow`, `StatusLabel`, `TextButton`, `EmptyNote`, `AlertBanner` and `DuesMeter` (see [../design.md](../design.md)). Route `/dashboard` (`frontend/src/router/index.js:12-17`), minimum role VOLUNTEER; `homePath` for VOLUNTEER and above (`frontend/src/stores/authStore.js:41`). Guards: [../authentication.md](../authentication.md).

## State
No Pinia store for data; Options API local `data()` (`frontend/src/views/Dashboard.vue:112-127`), with `authStore` / `appStore` from `setup()` (`:103-111`).
- `stats` -> `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}`, initial zeros
- `recentPayments`, `overdueMembers`, `activities` -> arrays from the dashboard endpoints
- `remindingIds` -> member ids with a send-reminder request in flight (disables that row's button)
- `loaded`, `loadError` -> `loaded` turns true once the first load finished (the hero shows nothing before that, so a zero never flashes); `loadError` shows an alert and the error also goes to `console.error`
- Computed (`:128-141`): `activeCount` (from `stats.activeMembers`), `behindMembers` (the overdue members, kept to those with `active` as a safeguard, longest overdue first; the endpoint is active-only), `paidCount` = `activeCount` minus `behindMembers.length`, never below 0. `DuesMeter` takes `activeCount` and `paidCount`; with no active members the meter is replaced by an empty state.

## Actions
- `loadData()` (`:146`) -> `Promise.all` of 4 calls, then assigns all state; runs on `created` (`:142-144`) and after a successful reminder. One failure aborts the whole assignment (nothing updates).
- `sendReminder(member)` (`:175`) -> `api.sendToMember(member.id, buildReminderRequest(member))` (the body has `type: 'REMINDER'`, so the message is stored as a reminder), toasts success or the server error (both titled "Send reminder", `:181`, `:189`), then `loadData()`.
- `formatDate(date)` (`:172`) -> `new Date(date).toLocaleDateString()`
- `formatMoney(amount)` (shared, `frontend/src/utils/index.js:42`, exposed from `setup()` at `:108`) -> US dollars with two decimals; the same helper formats the Payments screen

## Collaborators
| api.js method | Request | Backend |
|---------------|---------|---------|
| `getDashboardStats` (`frontend/src/services/api.js:472`) | GET `/dashboard/stats` | [dashboard-controller.md](dashboard-controller.md) |
| `getRecentPayments` (`:522`) | GET `/dashboard/recent-payments` | same |
| `getOverdueMembers` (`:526`) | GET `/dashboard/overdue-members` | same |
| `getRecentActivities` (`:530`) | GET `/dashboard/recent-activities` | same |
| `sendToMember` (`:423`) | POST `/communications/send-to-member/{memberId}` | `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:104` (STAFF+) |

Methods return `response.data` (`frontend/src/services/api.js:285-288`).

## Errors
- 401: api.js interceptor refreshes the token, retries, else clears auth and redirects (`frontend/src/services/api.js:105-171`; see [../authentication.md](../authentication.md)).
- Load failures: an alert at the top ("The overview did not load. Reload the page, or sign in again if it keeps happening.", `:5-7`) and a console log (`:166`). Send-reminder failures show an error toast with the server message (`:186-192`). The server no longer answers 200 with zeros when it fails: a failure is a 500 and reaches this banner.
- A role-denied visit to another page lands here (VOLUNTEER and above) with an "Access denied" warning toast raised by the router guard (`frontend/src/router/index.js:108-118`); the URL has no query parameter and this view reads nothing.

## Side effects
- "Send reminder" sends one real email to that member through the backend (background thread); the response returns before delivery, so a `SENT`/`FAILED` result is only visible via the deliveries endpoint.
- The button is rendered only for STAFF+ (`:28`), matching the server's `hasRole('STAFF')`.

## Gotchas
- The dashboard endpoints no longer catch exceptions (see [dashboard-controller.md](dashboard-controller.md#errors)), so the error alert also covers a server failure.
- `formatDate` here still does `new Date(date).toLocaleDateString()`: a `YYYY-MM-DD` date parses as UTC and can show a day early west of UTC, unlike `formatDate` in `frontend/src/utils/index.js` used on Members and Payments.
- Activity rows have no icon; the type is not shown. The backend only emits `payment` and `communication`.
- The "Member" column reads the nested `payment.member?.name` (`:57`); payments carry no `memberId` (`src/main/java/io/github/membertracker/domain/model/Payment.java:18`), but the nested `member` is always loaded (`payment.member_id` is NOT NULL, `src/main/resources/db/sql/001.schema-creation.sql:23`; mapped at `src/main/java/io/github/membertracker/infrastructure/persistence/repository/PaymentDbRepository.java:77`), so the `Unknown` fallback is not expected to show.
