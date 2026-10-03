# Dashboard

`frontend/src/views/Dashboard.vue`

Home screen (nav label "Overview"): a "Who needs a call" hero (the sentence "N of M active members are paid up", the dues meter, the list of active members who are behind, each with a Send reminder link), a quiet row with this month's payments and active members, then ruled lists of recent payments and recent activity, each with a plain empty state. Route `/dashboard` (`frontend/src/router/index.js:13-16`), minimum role VOLUNTEER; `homePath` for VOLUNTEER and above (`frontend/src/stores/authStore.js:43`). Guards: [../authentication.md](../authentication.md).

## State
No Pinia store for data; Options API local `data()` (`frontend/src/views/Dashboard.vue:108-123`), with `authStore` / `appStore` from `setup()` (`:101-107`).
- `stats` -> `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}`, initial zeros
- `recentPayments`, `overdueMembers`, `activities` -> arrays from the dashboard endpoints
- `remindingIds` -> member ids with a send-reminder request in flight (disables that row's button)
- `loaded`, `loadError` -> `loaded` turns true once the first load finished (the hero shows nothing before that, so a zero never flashes); `loadError` shows an alert and the error also goes to `console.error`
- Computed (`:124-140`): `activeCount` (from `stats.activeMembers`), `behindMembers` (overdue members with `active`, longest overdue first), `inactiveBehindCount` (the rest, shown as a note), `paidCount` = `activeCount` minus `behindMembers.length`, never below 0. `DuesMeter` takes `activeCount` and `paidCount`; with no active members the meter is replaced by an empty state.

## Actions
- `loadData()` (`:145`) -> `Promise.all` of 4 calls, then assigns all state; runs on `created` (`:141`) and after a successful reminder. One failure aborts the whole assignment (nothing updates).
- `sendReminder(member)` (`:177`) -> `api.sendToMember(member.id, buildReminderRequest(member))`, toasts success or the server error (both titled "Send reminder", `:181`, `:189`), then `loadData()`.
- `formatDate(date)` (`:171`) -> `new Date(date).toLocaleDateString()`
- `formatAmount(amount)` (`:174`) -> US dollars with two decimals through `Intl.NumberFormat` (`:96`)

## Collaborators
| api.js method | Request | Backend |
|---------------|---------|---------|
| `getDashboardStats` (`frontend/src/services/api.js:522`) | GET `/dashboard/stats` | [dashboard-controller.md](dashboard-controller.md) |
| `getRecentPayments` (`:526`) | GET `/dashboard/recent-payments` | same |
| `getOverdueMembers` (`:530`) | GET `/dashboard/overdue-members` | same |
| `getRecentActivities` (`:534`) | GET `/dashboard/recent-activities` | same |
| `sendToMember` (`:427`) | POST `/communications/send-to-member/{memberId}` | `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:114` (STAFF+) |

Methods return `response.data` (`frontend/src/services/api.js:285-288`).

## Errors
- 401: api.js interceptor refreshes the token, retries, else clears auth and redirects (`frontend/src/services/api.js:92-170`; see [../authentication.md](../authentication.md)).
- Load failures: an alert at the top ("The overview did not load. Reload the page, or sign in again if it keeps happening.", `:8`) and a console log (`:165`). Send-reminder failures show an error toast with the server message (`:188-194`).
- A role-denied visit to another page lands here (VOLUNTEER and above) with an "Access denied" warning toast raised by the router guard (`frontend/src/router/index.js:103-112`); the URL has no query parameter and this view reads nothing.

## Side effects
- "Send reminder" sends one real email to that member through the backend (background thread); the response returns before delivery, so a `SENT`/`FAILED` result is only visible via the deliveries endpoint.
- The button is rendered only for STAFF+ (`:31`), matching the server's `hasRole('STAFF')`.

## Gotchas
- Backend errors are swallowed as 200 with zeros/empty lists (see dashboard-controller.md), so the view's error alert only appears for network and auth failures.
- Activity rows have no icon; the type is not shown. The backend only emits `payment` and `communication`.
- The "Member" column reads the nested `payment.member?.name` (`:67`); payments carry no `memberId` (`src/main/java/io/github/membertracker/domain/model/Payment.java:18`), but the nested `member` is always loaded (`payment.member_id` is NOT NULL, `src/main/resources/db/sql/001.schema-creation.sql:23`; mapped at `src/main/java/io/github/membertracker/infrastructure/persistence/repository/PaymentDbRepository.java:77`), so the `Unknown` fallback is not expected to show.
