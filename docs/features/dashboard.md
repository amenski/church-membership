# Dashboard

The home screen for staff and volunteers: four headline numbers, the latest payments, members who are overdue, and a short activity timeline.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the dashboard | VOLUNTEER | `/dashboard` (`frontend/src/router/index.js:11-16`, guard `:101-105`) |
| Read the four headline numbers | VOLUNTEER | `GET /api/dashboard/stats` (`src/main/java/io/github/membertracker/infrastructure/DashboardController.java:47-50`) |
| See recent payments | VOLUNTEER | `GET /api/dashboard/recent-payments` (`DashboardController.java:102-105`) |
| See overdue members | VOLUNTEER | `GET /api/dashboard/overdue-members` (`DashboardController.java:127-130`) |
| See the activity timeline | VOLUNTEER | `GET /api/dashboard/recent-activities` (`DashboardController.java:140-143`) |
| Send a reminder to one overdue member | STAFF | Send Reminder button (`frontend/src/views/Dashboard.vue:86-93`), `POST /api/communications/send-to-member/{memberId}` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:114-130`) |

Role view of the screen:
- VOLUNTEER sees all four blocks; the Actions column of the overdue table is empty (the button needs `isStaff`, `Dashboard.vue:87`, `frontend/src/stores/authStore.js:41`).
- STAFF and ADMIN also see Send Reminder on each overdue row.
- MEMBER cannot open it: the route sends them to `/profile` with `?error=access_denied`, which the profile screen does not display (`frontend/src/router/index.js:102-104`). The four endpoints answer 403.
- The dashboard is the home page for VOLUNTEER and above; MEMBER's home is `/profile` (`authStore.js:43`). Opening `/` or `/login` while signed in also lands there (`frontend/src/router/index.js:90-95`).

## How it works
### Load the dashboard
1. On open, the screen asks the four endpoints at the same time and fills the blocks when all four answered (`Dashboard.vue:156-181`).
2. If one request fails, none of the blocks updates: the numbers stay 0 and the lists empty, and nothing is shown to the user (`Dashboard.vue:178-180`).
3. The screen does not refresh by itself; reload the page, or send a reminder, to reload (`Dashboard.vue:195`).

### Read the four headline numbers
1. Total Members: every member, active or not (`DashboardController.java:55-56`).
2. Active Members: members marked active (`DashboardController.java:59-60`).
3. Overdue Members: members whose missed-months counter is 1 or more, active or not (`DashboardController.java:63-64`; counter rules in [payment-reminders.md](payment-reminders.md)).
4. This Month's Revenue: the sum of payment amounts whose billing month (the `period`) is the current month (`DashboardController.java:67-87`).
- Revenue follows the billing month, not the day the money was recorded: a payment made today for last month is not counted; a payment made earlier for this month is. The Payments screen's "this month" card uses the payment date instead, so the two can differ ([payments.md](payments.md)).
- The cards show the raw numbers, with `$` in front of revenue and no rounding (`Dashboard.vue:9`, `:17`, `:25`, `:33`).

### Recent payments
1. The server sorts all payments by payment date, newest first, and returns the first 10 (`DashboardController.java:106-121`).
2. The table shows date, member name and amount (`Dashboard.vue:41-65`). "Unknown" is shown only when the member name is missing (`Dashboard.vue:57`); every payment has a member, so it is not expected to appear.

### Overdue members
1. The list is every member with a counter of 1 or more, with name and "Months Overdue" (`DashboardController.java:133`, `Dashboard.vue:68-101`).
2. It is not sorted and not limited. Inactive members with a counter above 0 are listed too.
3. Members appear here once the monthly counter job has run on the 1st; see [payment-reminders.md](payment-reminders.md).

### Activity timeline
1. The server merges the 5 newest payments (by payment date) and the 5 newest communications (by created date), sorts by date, newest first, and returns 10 (`DashboardController.java:147-208`).
2. Payment lines read "Payment received: $<amount>"; communication lines read "Communication sent: <title>" (`DashboardController.java:167`, `:194`). Communications count when created, including a message that was created but never sent.
3. It is derived from payments and communications, not from the `activity_log` table, which nothing writes to (`src/main/resources/db/sql/001.schema-creation.sql:70-82`). Member changes, sign-ins and exports never appear.
4. The icon follows the type: payment, communication, or member (never produced) (`Dashboard.vue:207-214`).

### Send a reminder to one overdue member (STAFF+)
1. Click Send Reminder on a row (`Dashboard.vue:86-93`). There is no confirmation.
2. The screen sends the title "Payment reminder" and the text "Dear <name>, this is a reminder that your membership payment is overdue." (`frontend/src/utils/communicationPayload.js:10-15`) to `POST /api/communications/send-to-member/{id}` (`Dashboard.vue:188`).
3. While the request runs, that row's button is disabled (`Dashboard.vue:89`, `:186`, `:204`).
4. The server creates a message with one PENDING delivery and sends the email in the background ([communications.md](communications.md#send-to-one-member-staff)).
5. Success: toast "Reminder sent to <name>" (it means the send was accepted, not that the email arrived), and the dashboard reloads; the new message shows in the timeline (`Dashboard.vue:189-195`).
6. Failure (403, unknown member): error toast with the server's message (`Dashboard.vue:196-202`).
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
- The overdue list and count include inactive members, and a reminder can be sent to them.
- Dashboard dates parse the server's date text as UTC, so in time zones west of UTC a date can show one day early (`Dashboard.vue:182-184`).
- The reminder text is fixed and the message is recorded as an announcement, not as type REMINDER (`communicationPayload.js:10-15`, `CommunicationController.java:137`).
- Load failures are only logged to the console (`Dashboard.vue:179`).
- The money cards and lists use a hard-coded `$` and unformatted amounts (`Dashboard.vue:33`, `:58`, `DashboardController.java:167`).
- `/stats` returns `monthlyRevenue` as `0` on failure and as a decimal on success (`DashboardController.java:87`, `:97`).

## Related
- [dashboard-controller.md](dashboard-controller.md): endpoints and data loading
- [dashboard-view.md](dashboard-view.md): screen state and actions
- [communications.md](communications.md), [payment-reminders.md](payment-reminders.md), [payments.md](payments.md), [members.md](members.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
