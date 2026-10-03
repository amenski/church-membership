# Dashboard

The home screen for staff and volunteers (nav label "Overview"): who is paid up and who needs a call, this month's payments, the latest payments, and a short activity list.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the dashboard | VOLUNTEER | `/dashboard` (`frontend/src/router/index.js:12-17`, guard `:102-106`) |
| Read the headline figures | VOLUNTEER | `GET /api/dashboard/stats` (`src/main/java/io/github/membertracker/infrastructure/DashboardController.java:47-50`) |
| See recent payments | VOLUNTEER | `GET /api/dashboard/recent-payments` (`DashboardController.java:102-105`) |
| See overdue members | VOLUNTEER | `GET /api/dashboard/overdue-members` (`DashboardController.java:127-130`) |
| See recent activity | VOLUNTEER | `GET /api/dashboard/recent-activities` (`DashboardController.java:140-143`) |
| Send a reminder to one overdue member | STAFF | Send reminder link (`frontend/src/views/Dashboard.vue:30-39`), `POST /api/communications/send-to-member/{memberId}` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:114-130`) |

Role view of the screen:
- VOLUNTEER sees everything on the screen; the overdue rows have no Send reminder link (it needs `isStaff`, `Dashboard.vue:31`, `frontend/src/stores/authStore.js:41`).
- STAFF and ADMIN also see Send reminder on each overdue row.
- MEMBER cannot open it: the route guard shows an "Access denied" warning toast and sends them to `/profile` (`frontend/src/router/index.js:103-112`). The four endpoints answer 403.
- The dashboard is the home page for VOLUNTEER and above; MEMBER's home is `/profile` (`authStore.js:43`). Opening `/` or `/login` while signed in also lands there (`frontend/src/router/index.js:91-96`).

## How it works
### Load the dashboard
1. On open, the screen asks the four endpoints at the same time and fills the blocks when all four answered (`Dashboard.vue:145-170`).
2. If one request fails, none of the blocks updates: the numbers stay 0 and the lists empty, and an alert says the overview did not load (`Dashboard.vue:8`, `:165-167`).
3. The screen does not refresh by itself; reload the page, or send a reminder, to reload (`Dashboard.vue:187`).

### Read the headline figures
1. Total Members: every member, active or not (`DashboardController.java:55-56`).
2. Active Members: members marked active (`DashboardController.java:59-60`).
3. Overdue Members: members whose missed-months counter is 1 or more, active or not (`DashboardController.java:63-64`; counter rules in [payment-reminders.md](payment-reminders.md)).
4. This Month's Revenue: the sum of payment amounts whose billing month (the `period`) is the current month (`DashboardController.java:67-87`).
- Revenue follows the billing month, not the day the money was recorded: a payment made today for last month is not counted; a payment made earlier for this month is. The Payments screen's "this month" card uses the payment date instead, so the two can differ ([payments.md](payments.md)).
- The screen builds its hero from these: "N of M active members are paid up" with M = Active Members and N = M minus the overdue members who are active (`Dashboard.vue:125-140`), drawn as the dues meter, one segment per active member (capped at 40). With no active members it says so instead. Total Members and the Overdue count are not shown.
- "This month's payments" and "Active members" are a quiet row below the list (`Dashboard.vue:50-59`). Amounts are formatted as US dollars with two decimals (`Dashboard.vue:96`, `:174`).

### Recent payments
1. The server sorts all payments by payment date, newest first, and returns the first 10 (`DashboardController.java:106-121`).
2. A ruled list shows date, member name and amount (`Dashboard.vue:62-72`). "Unknown" is shown only when the member name is missing (`Dashboard.vue:67`); every payment has a member, so it is not expected to appear. With no payments it says "No payments recorded yet. Record the first one under Payments."

### Overdue members
1. The endpoint returns every member with a counter of 1 or more (`DashboardController.java:133`). The screen lists only the active ones, longest overdue first, each as "N months behind" ("1 month behind" for one) (`Dashboard.vue:26-41`, `:129-133`).
2. The list is not limited. Inactive members with a counter above 0 are left out of it, with a note "N inactive members are not shown" (`Dashboard.vue:43-45`, `:134-136`). With none overdue it says "No overdue members. Everyone is paid up for this month."
3. Members appear here once the monthly counter job has run on the 1st; see [payment-reminders.md](payment-reminders.md).

### Recent activity
1. The server merges the 5 newest payments (by payment date) and the 5 newest communications (by created date), sorts by date, newest first, and returns 10 (`DashboardController.java:147-208`).
2. Payment lines read "Payment received: $<amount>"; communication lines read "Communication sent: <title>" (`DashboardController.java:167`, `:194`). Communications count when created, including a message that was created but never sent.
3. It is derived from payments and communications, not from the `activity_log` table, which nothing writes to (`src/main/resources/db/sql/001.schema-creation.sql:70-82`). Member changes, sign-ins and exports never appear.
4. Each activity is a plain row, date and text, with no icon (`Dashboard.vue:74-83`). With none it says "Nothing has happened yet. Payments and messages will show up here."

### Send a reminder to one overdue member (STAFF+)
1. Click Send reminder on a row (`Dashboard.vue:30-39`). There is no confirmation.
2. The screen sends the title "Payment reminder" and the text "Dear <name>, this is a reminder that your membership payment is overdue." (`frontend/src/utils/communicationPayload.js:10-15`) to `POST /api/communications/send-to-member/{id}` (`Dashboard.vue:180`).
3. While the request runs, that row's button is disabled (`Dashboard.vue:34`, `:178`, `:196`).
4. The server creates a message with one PENDING delivery and sends the email in the background ([communications.md](communications.md#send-to-one-member-staff)).
5. Success: toast "Send reminder: Reminder sent to <name>" (it means the send was accepted, not that the email arrived), and the dashboard reloads; the new message shows in the activity list (`Dashboard.vue:181-187`).
6. Failure (403, unknown member): error toast with the server's message (`Dashboard.vue:188-194`).
7. The member stays on the list: sending a reminder does not change the counter. The message also appears under Communications as an announcement.

## Rules
- Role checks are on the server and on the screen; hiding the button for VOLUNTEER is not the only guard (`CommunicationController.java:115`).
- Overdue means a counter of 1 or more, whatever the member's status; the automatic reminder uses a higher threshold ([payment-reminders.md](payment-reminders.md)).
- Lists are limited to 10 payments and 10 activities; there is no paging or date range.
- Numbers come from the database at load time; nothing is cached or updated live.

## Known issues
- **Errors look like an empty dashboard:** every dashboard endpoint catches any exception and answers 200 with zeros or `[]` (`DashboardController.java:90-98`, `:122-124`, `:135-137`, `:209-211`); only `/stats` logs it. A database outage reads as "0 members, $0, no payments". Logged in [../todo.md](../todo.md).
- **Heavy loading:** each endpoint loads whole tables and filters in Java; the three payment-based blocks load all payments three times per page load, and the timeline also loads all communications ([dashboard-controller.md](dashboard-controller.md#data-loaded-per-request)). Logged in [../todo.md](../todo.md).
- Revenue is by billing month while the Payments screen is by payment date, so the two "this month" figures can disagree.
- The overdue endpoint and the `overdueMembers` count include inactive members. The screen hides them from the list (and notes how many), so a reminder can no longer be sent to them from here.
- Dashboard dates parse the server's date text as UTC, so in time zones west of UTC a date can show one day early (`Dashboard.vue:171-173`).
- The reminder text is fixed and the message is recorded as an announcement, not as type REMINDER (`communicationPayload.js:10-15`, `CommunicationController.java:137`).
- Load failures show an alert and are logged to the console (`Dashboard.vue:8`, `:165`).
- Amounts on the screen are formatted as US dollars, whatever the congregation's currency (`Dashboard.vue:96`); the server's activity text still has `$` baked in (`DashboardController.java:167`).
- `/stats` returns `monthlyRevenue` as `0` on failure and as a decimal on success (`DashboardController.java:87`, `:97`).

## Related
- [dashboard-controller.md](dashboard-controller.md): endpoints and data loading
- [dashboard-view.md](dashboard-view.md): screen state and actions
- [communications.md](communications.md), [payment-reminders.md](payment-reminders.md), [payments.md](payments.md), [members.md](members.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
