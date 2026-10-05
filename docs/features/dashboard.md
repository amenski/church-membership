# Dashboard

The home screen for staff and volunteers (nav label "Overview"): a facts strip, who owes dues month by month, who needs a call, the latest payments, and a short activity list.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the dashboard | VOLUNTEER | `/dashboard` (`frontend/src/router/index.js`) |
| Read the headline figures | VOLUNTEER | `GET /api/dashboard/stats` (`src/main/java/io/github/membertracker/infrastructure/DashboardController.java`) |
| See the amount collected per month | VOLUNTEER | `GET /api/dashboard/collected-by-month` (`DashboardController.java`); drawn on Payments, not on the Overview |
| See the latest payments | VOLUNTEER | one page of `GET /api/payments/page` ([payment-controller.md](payment-controller.md)); `GET /api/dashboard/recent-payments` exists but the screen does not call it |
| See who owes dues | VOLUNTEER | the dues ledger and Call this week, built from `GET /api/members` and `GET /api/payments/paid-months`; `GET /api/dashboard/overdue-members` exists but the screen does not call it |
| See recent activity | VOLUNTEER | `GET /api/dashboard/recent-activities` (`DashboardController.java`) |
| Open the Messages screen from a member who owes | STAFF | "Send reminder" link in Call this week; it goes to `/communications` and sends nothing itself ([communications.md](communications.md)) |

Role view of the screen:
- VOLUNTEER sees everything on the screen; the Call this week rows have no "Send reminder" link (it needs `isStaff`, `Dashboard.vue`, `frontend/src/stores/authStore.js`).
- STAFF and ADMIN also see "Send reminder" on each Call this week row.
- MEMBER cannot open it: the route guard shows an "Access denied" warning toast and sends them to My dues (`frontend/src/router/index.js`). The dashboard endpoints answer 403.
- The dashboard is the home page for VOLUNTEER and above; MEMBER's home is `/my-dues` (`homePath` in `frontend/src/stores/authStore.js`). Opening `/login` while signed in also lands there (`frontend/src/router/index.js`).

## How it works
### Load the dashboard
1. On open, the screen asks four endpoints at the same time (stats, one page of payments, all members, recent activities) and fills the blocks when all four answered; then it loads the paid months for the year strips and the communications for the failed-reminders tile (`Dashboard.vue`; both fail on their own without hiding the rest).
2. If one of the first four requests fails, none of the blocks updates: the numbers stay 0 and the lists empty, and a banner says the overview did not load (`Dashboard.vue`). The server never hides a failure behind zeros (see Known issues), so this banner also covers a database outage.
3. The screen does not refresh by itself; reload the page to reload (`Dashboard.vue`).

### Read the headline figures
1. Total members: every member except the archived (`src/main/java/io/github/membertracker/usecase/GetDashboardStatsUseCase.java`).
2. Active members: members with status MEMBER, the ones who owe dues (`GetDashboardStatsUseCase.java`).
3. Overdue members: MEMBER-status members whose missed-months counter is 1 or more (`GetDashboardStatsUseCase.java`; counter rules in [payment-reminders.md](payment-reminders.md)). Any other status is never counted, so this number and the Call this week list agree.
4. This month's revenue: the sum of payment amounts whose billing month (the `period`) is the current month (`GetDashboardStatsUseCase.java`).
- Revenue follows the billing month, not the day the money was recorded: a payment made today for last month is not counted; a payment made earlier for this month is. The Payments screen's "This month" figure uses the same rule, so the two agree ([payments.md](payments.md)).
- The screen leads with a facts strip of up to four tiles: Collected in the month (`monthlyRevenue`), Paid up (N of M dues-paying members), Behind on dues (members and total months unpaid) and Reminders (failed reminder deliveries, left out when that call fails). Total members are not shown. The exact layout is in [dashboard-view.md](dashboard-view.md). Amounts are formatted as US dollars with two decimals by the shared `formatMoney` (`frontend/src/utils/index.js`).

### Collected by month
Not on the Overview any more: the 12-month chart (`GET /api/dashboard/collected-by-month`) is on Payments, see [payments-view.md](payments-view.md). "Collected in <month>" stays in the facts strip.

### Recent payments
1. The screen asks `GET /api/payments/page?page=0&size=10&sort=paymentDate,desc`: the 10 newest payments, newest payment date first, then newest id ([payment-controller.md](payment-controller.md)). The dashboard's own `GET /api/dashboard/recent-payments` returns the same 10 but is not used by the screen.
2. A card "Latest payments" shows each row as the member's name, a detail line (month covered, method, paid on) and the amount, then an "All payments" link (`Dashboard.vue`). "Unknown" is shown only when the member name is missing; every payment has a member, so it is not expected to appear. With no payments it says "No payments recorded yet. Record the first one under Payments."

### Who owes dues
1. The screen no longer calls `GET /api/dashboard/overdue-members` (MEMBER-status members with a counter of 1 or more, the one furthest behind first). It works from the member list it already loads: members with status MEMBER and a counter above 0, longest first.
2. Call this week is the first three of them, with "N months behind", a Call link when the member has a phone and, for STAFF and above, "Send reminder" (a link to `/communications`). With none behind it says "Nobody is behind. There is no one to call."
3. The dues ledger ("Dues by member") lists the first 10 members who are behind or have not paid the current month, with a link "See all" to `/members?dues=behind` when there are more; see [dashboard-view.md](dashboard-view.md).
4. Members appear as behind once the monthly counter job has run on the 1st; see [payment-reminders.md](payment-reminders.md).

### Recent activity
1. The server merges the 5 newest payments (by payment date) and the 5 newest communications (by created date), sorts by date, newest first, and returns 10 (`DashboardController.java`).
2. Payment lines read "Payment received: $<amount>"; communication lines read "Communication sent: <title>" (`DashboardController.java`). A communication is only stored when it is sent (there are no drafts), so every one counts.
3. It is derived from payments and communications, not from the `activity_log` table that the Activity screen reads ([activity.md](activity.md)). Member changes, sign-ins and exports never appear here.
4. Each activity is a plain row, date and text, with no icon, in a "Recent activity" card (`Dashboard.vue`). With none it says "Nothing has happened yet. Payments and messages will show up here."

### Send reminder (STAFF+)
"Send reminder" on a Call this week row is a link to `/communications`. The Overview no longer sends mail itself, so there is no confirmation, no toast and no write here; the message is composed on Messages ([communications.md](communications.md)).

## Rules
- Role checks are on the server and on the screen; hiding the link for VOLUNTEER is not the only guard (`CommunicationController.java`).
- Overdue means a member with status MEMBER and a counter of 1 or more; the automatic reminder uses a threshold of its own ([payment-reminders.md](payment-reminders.md)). "Behind" and "paid up" apply to MEMBER-status members only.
- Lists are limited to 10 payments, 10 activities and 3 calls; the ledger shows 10 rows; there is no paging or date range.
- Numbers come from the database at load time; nothing is cached or updated live.

## Known issues
- Dashboard dates parse the server's date text as UTC (`new Date(date)`), so in time zones west of UTC a date can show one day early (`Dashboard.vue`); the Payments and Members screens use `formatDate`, which reads a date-only string as a local day.
- Load failures show a banner and are logged to the console (`Dashboard.vue`).
- Amounts on the screen are formatted as US dollars, whatever the congregation's currency; the server's activity text still has `$` baked in (`DashboardController.java`).

Fixed since the first version of this page: the endpoints no longer swallow exceptions (the class comment at `DashboardController.java` says why; a failure is a 500 and the screen shows its banner), the endpoints no longer load whole tables (counts, a `SUM` and `LIMIT` queries; [dashboard-controller.md](dashboard-controller.md#data-loaded-per-request)), and the overdue endpoint and the overdue count are MEMBER-status only.

## Related
- [dashboard-controller.md](dashboard-controller.md): endpoints and data loading
- [dashboard-view.md](dashboard-view.md): screen state and actions
- [communications.md](communications.md), [payment-reminders.md](payment-reminders.md), [payments.md](payments.md), [members.md](members.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)
