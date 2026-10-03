# Dashboard

`frontend/src/views/Dashboard.vue`

Home screen with stat cards, recent payments, overdue members (with a reminder button) and an activity timeline. Route `/dashboard` (`frontend/src/router/index.js:12-15`), minimum role VOLUNTEER; `homePath` for VOLUNTEER and above (`frontend/src/stores/authStore.js:43`). Guards: [../authentication.md](../authentication.md).

## State
No Pinia store; Options API local `data()` (`frontend/src/views/Dashboard.vue:128-140`).
- `stats` -> `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}`, initial zeros
- `recentPayments`, `overdueMembers`, `activities` -> arrays from the dashboard endpoints
- `members` -> full member list, used only to resolve payment member names
- No `isLoading` / `error` state: errors go to `console.error` only

## Actions
- `loadData()` (`:146`) -> `Promise.all` of 5 calls, then assigns all state; runs on `created` (`:142`) and after a reminder. One failure aborts the whole assignment (nothing updates).
- `sendReminder(member)` (`:178`) -> `api.createCommunication({memberId, subject, message})`, then `loadData()`.
- `formatDate(date)` (`:171`) -> `new Date(date).toLocaleDateString()`
- `getMemberName(memberId)` (`:174`) -> lookup in `members`, else `'Unknown'`
- `getActivityIcon(type)` (`:190`) -> Bootstrap icon per `payment` / `member` / `communication`

## Collaborators
| api.js method | Request | Backend |
|---------------|---------|---------|
| `getDashboardStats` (`frontend/src/services/api.js:518`) | GET `/dashboard/stats` | [dashboard-controller.md](dashboard-controller.md) |
| `getRecentPayments` (`:522`) | GET `/dashboard/recent-payments` | same |
| `getOverdueMembers` (`:526`) | GET `/dashboard/overdue-members` | same |
| `getRecentActivities` (`:530`) | GET `/dashboard/recent-activities` | same |
| `getMembers` (`:350`) | GET `/members` | `src/main/java/io/github/membertracker/infrastructure/MemberController.java:66` (VOLUNTEER+) |
| `createCommunication` (`:415`) | POST `/communications` | `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:77` (STAFF+) |

Methods return `response.data` (`frontend/src/services/api.js:285-288`).

## Errors
- 401: api.js interceptor refreshes the token, retries, else clears auth and redirects (`frontend/src/services/api.js:92-170`; see [../authentication.md](../authentication.md)).
- Other failures: logged to console (`:168`, `:187`); nothing shown to the user.
- Role-denied route access lands here via `?error=access_denied` redirect (`frontend/src/router/index.js:103`); this view does not read or display it.

## Side effects
- "Send Reminder" creates a Communication record for the backend; see Gotchas on whether that can work.

## Gotchas
- "Send Reminder" is broken: it posts `{memberId, subject, message}` but `Communication` requires `title` and `messageContent` (`src/main/java/io/github/membertracker/domain/model/Communication.java:25-30`), so validation returns 400. It also needs STAFF (`CommunicationController.java:78`), so a VOLUNTEER would get 403 even with a valid body, though the button shows for all. And `POST /communications` only stores the record; it does not send an email.
- "Member" column always shows `Unknown`: payments carry a nested `member` object, not `memberId` (`src/main/java/io/github/membertracker/domain/model/Payment.java:18`), but the view reads `payment.memberId` (`:58`, `:174-176`).
- Backend errors are swallowed as 200 with zeros/empty lists (see dashboard-controller.md), so the view cannot show an error state.
- Activity type `member` has an icon and style (`:192-194`, `:239`) but the backend never emits it.
- Loads `/members` in full plus the full-table dashboard queries on every load and after each reminder.
