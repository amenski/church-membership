# Dashboard

`frontend/src/views/Dashboard.vue`

Home screen with stat cards, recent payments, overdue members (with a reminder button) and an activity timeline. Route `/dashboard` (`frontend/src/router/index.js:13-16`), minimum role VOLUNTEER; `homePath` for VOLUNTEER and above (`frontend/src/stores/authStore.js:43`). Guards: [../authentication.md](../authentication.md).

## State
No Pinia store for data; Options API local `data()` (`frontend/src/views/Dashboard.vue:142-155`), with `authStore` / `appStore` from `setup()` (`:136-141`).
- `stats` -> `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}`, initial zeros
- `recentPayments`, `overdueMembers`, `activities` -> arrays from the dashboard endpoints
- `remindingIds` -> member ids with a send-reminder request in flight (disables that row's button)
- No `isLoading` / `error` state: load errors go to `console.error` only

## Actions
- `loadData()` (`:160`) -> `Promise.all` of 4 calls, then assigns all state; runs on `created` (`:156`) and after a successful reminder. One failure aborts the whole assignment (nothing updates).
- `sendReminder(member)` (`:185`) -> `api.sendToMember(member.id, buildReminderRequest(member))`, toasts success or the server error (`:189`, `:197`), then `loadData()`.
- `formatDate(date)` (`:182`) -> `new Date(date).toLocaleDateString()`
- `getActivityIcon(type)` (`:207`) -> Bootstrap icon per `payment` / `member` / `communication`

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
- Load failures: logged to console (`:179`); nothing shown to the user. Send-reminder failures show an error toast with the server `detail` (`:196-202`).
- A role-denied visit to another page lands here (VOLUNTEER and above) with an "Access denied" warning toast raised by the router guard (`frontend/src/router/index.js:103-112`); the URL has no query parameter and this view reads nothing.

## Side effects
- "Send Reminder" sends one real email to that member through the backend (background thread); the response returns before delivery, so a `SENT`/`FAILED` result is only visible via the deliveries endpoint.
- The button is rendered only for STAFF+ (`:87`), matching the server's `hasRole('STAFF')`.

## Gotchas
- Backend errors are swallowed as 200 with zeros/empty lists (see dashboard-controller.md), so the view cannot show an error state.
- Activity type `member` has an icon and style (`:209-211`, `:256`) but the backend never emits it.
- The "Member" column reads the nested `payment.member?.name` (`:57`); payments carry no `memberId` (`src/main/java/io/github/membertracker/domain/model/Payment.java:18`), but the nested `member` is always loaded (`payment.member_id` is NOT NULL, `src/main/resources/db/sql/001.schema-creation.sql:23`; mapped at `src/main/java/io/github/membertracker/infrastructure/persistence/repository/PaymentDbRepository.java:77`), so the `Unknown` fallback is not expected to show.
