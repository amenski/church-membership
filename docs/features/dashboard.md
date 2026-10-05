# Dashboard

The home screen for staff and volunteers (nav label "Overview"): who is paid up and who needs a call, this month's payments, the latest payments, and a short activity list.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the dashboard | VOLUNTEER | `/dashboard` (`frontend/src/router/index.js`) |
| Read the headline figures | VOLUNTEER | `GET /api/dashboard/stats` (`src/main/java/io/github/membertracker/infrastructure/DashboardController.java`) |
| See the amount collected per month | VOLUNTEER | `GET /api/dashboard/collected-by-month` (`DashboardController.java`) |
| See recent payments | VOLUNTEER | `GET /api/dashboard/recent-payments` (`DashboardController.java`) |
| See overdue members | VOLUNTEER | `GET /api/dashboard/overdue-members` (`DashboardController.java`) |
| See recent activity | VOLUNTEER | `GET /api/dashboard/recent-activities` (`DashboardController.java`) |
| Send a reminder to one overdue member | STAFF | "Send reminder" button (`frontend/src/views/Dashboard.vue`), `POST /api/communications/send-to-member/{memberId}` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`) |

Role view of the screen:
- VOLUNTEER sees everything on the screen; the overdue rows have no "Send reminder" button (it needs `isStaff`, `Dashboard.vue`, `frontend/src/stores/authStore.js`).
- STAFF and ADMIN also see "Send reminder" on each overdue row.
- MEMBER cannot open it: the route guard shows an "Access denied" warning toast and sends them to `/profile` (`frontend/src/router/index.js`). The four endpoints answer 403.
- The dashboard is the home page for VOLUNTEER and above; MEMBER's home is `/profile` (`frontend/src/stores/authStore.js`). Opening `/` or `/login` while signed in also lands there (`frontend/src/router/index.js`).

## How it works
### Load the dashboard
1. On open, the screen asks the four endpoints at the same time and fills the blocks when all four answered (`Dashboard.vue`).
2. If one request fails, none of the blocks updates: the numbers stay 0 and the lists empty, and a banner says the overview did not load (`Dashboard.vue`). The server never hides a failure behind zeros (see Known issues), so this banner also covers a database outage.
3. The screen does not refresh by itself; reload the page, or send a reminder, to reload (`Dashboard.vue`).

### Read the headline figures
1. Total members: every member, active or not (`src/main/java/io/github/membertracker/usecase/GetDashboardStatsUseCase.java`).
2. Active members: members marked active (`GetDashboardStatsUseCase.java`).
3. Overdue members: ACTIVE members whose missed-months counter is 1 or more (`GetDashboardStatsUseCase.java`; counter rules in [payment-reminders.md](payment-reminders.md)). An inactive member is never counted, so this number, the list below and the dues meter agree.
4. This month's revenue: the sum of payment amounts whose billing month (the `period`) is the current month (`GetDashboardStatsUseCase.java`).
- Revenue follows the billing month, not the day the money was recorded: a payment made today for last month is not counted; a payment made earlier for this month is. The Payments screen's "This month" figure uses the same rule, so the two agree ([payments.md](payments.md)).
- The screen leads with four `StatTile`s built from these: Active members, Paid up (active members minus the overdue members on the list), Behind (the overdue count) and This month's payments. With no active members the overdue list shows its empty state instead. Total members are not shown separately.
- "This month's payments" and "Active members" are a quiet row below the list (`Dashboard.vue`). Amounts are formatted as US dollars with two decimals by the shared `formatMoney` (`frontend/src/utils/index.js`).

### Collected by month
Not on the Overview any more: the 12-month chart (`GET /api/dashboard/collected-by-month`) is on Payments, see [payments-view.md](payments-view.md). "Collected in <month>" stays in the facts strip.

### Recent payments
1. The server returns the 10 newest payments, newest payment date first, then newest id, limited in the query (`DashboardController.java`).
2. A ruled list shows date, member name and amount (`Dashboard.vue`). "Unknown" is shown only when the member name is missing; every payment has a member, so it is not expected to appear. With no payments it says "No payments recorded yet. Record the first one under Payments."

### Overdue members
1. The endpoint returns ACTIVE members with a counter of 1 or more, the one furthest behind first (`DashboardController.java`, `src/main/java/io/github/membertracker/usecase/GetMembersWithMissedPaymentsUseCase.java`). The screen lists them as "N months behind" ("1 month behind" for one) (`Dashboard.vue`); it still filters on `active` and sorts again (`Dashboard.vue`), which is harmless.
2. The list is not limited. There is no note about inactive members any more: the endpoint no longer returns them. With none overdue it says "No overdue members. Everyone is paid up for this month."
3. Members appear here once the monthly counter job has run on the 1st; see [payment-reminders.md](payment-reminders.md).

### Recent activity
1. The server merges the 5 newest payments (by payment date) and the 5 newest communications (by created date), sorts by date, newest first, and returns 10 (`DashboardController.java`).
2. Payment lines read "Payment received: $<amount>"; communication lines read "Communication sent: <title>" (`DashboardController.java`). A communication is only stored when it is sent (there are no drafts), so every one counts.
3. It is derived from payments and communications, not from the `activity_log` table, which nothing writes to (`src/main/resources/db/sql/001.schema-creation.sql`). Member changes, sign-ins and exports never appear.
4. Each activity is a plain row, date and text, with no icon (`Dashboard.vue`). With none it says "Nothing has happened yet. Payments and messages will show up here."

### Send a reminder to one overdue member (STAFF+)
1. Click "Send reminder" on a row (`Dashboard.vue`). There is no confirmation.
2. The screen sends the title "Payment reminder", the text "Dear <name>, this is a reminder that your membership payment is overdue." and the type REMINDER (`frontend/src/utils/communicationPayload.js`) to `POST /api/communications/send-to-member/{id}` (`Dashboard.vue`).
3. While the request runs, that row's button is disabled (`Dashboard.vue`).
4. The server creates a message with one PENDING delivery and sends the email in the background ([communications.md](communications.md#send-to-one-member-staff)).
5. Success: toast "Send reminder: Reminder sent to <name>" (it means the send was accepted, not that the email arrived), and the overview reloads; the new message shows in the activity list (`Dashboard.vue`).
6. Failure (403, unknown member): error toast with the server's message (`Dashboard.vue`).
7. The member stays on the list: sending a reminder does not change the counter. The message also appears under Messages, typed "Reminder".

## Rules
- Role checks are on the server and on the screen; hiding the button for VOLUNTEER is not the only guard (`CommunicationController.java`).
- Overdue means an ACTIVE member with a counter of 1 or more; the automatic reminder uses a higher threshold ([payment-reminders.md](payment-reminders.md)). "Behind" and "paid up" apply to active members only.
- Lists are limited to 10 payments and 10 activities; there is no paging or date range.
- Numbers come from the database at load time; nothing is cached or updated live.

## Known issues
- Dashboard dates parse the server's date text as UTC (`new Date(date)`), so in time zones west of UTC a date can show one day early (`Dashboard.vue`); the Payments and Members screens use `formatDate`, which reads a date-only string as a local day.
- The reminder text is fixed (`communicationPayload.js`).
- Load failures show a banner and are logged to the console (`Dashboard.vue`).
- Amounts on the screen are formatted as US dollars, whatever the congregation's currency; the server's activity text still has `$` baked in (`DashboardController.java`).

Fixed since the first version of this page: the endpoints no longer swallow exceptions (the class comment at `DashboardController.java` says why; a failure is a 500 and the screen shows its banner), the endpoints no longer load whole tables (counts, a `SUM` and `LIMIT` queries; [dashboard-controller.md](dashboard-controller.md#data-loaded-per-request)), the overdue endpoint and the overdue count are active-only, and a reminder is stored as a REMINDER.

## Related
- [dashboard-controller.md](dashboard-controller.md): endpoints and data loading
- [dashboard-view.md](dashboard-view.md): screen state and actions
- [communications.md](communications.md), [payment-reminders.md](payment-reminders.md), [payments.md](payments.md), [members.md](members.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
