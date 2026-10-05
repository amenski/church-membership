# Payment reminders

An automatic monthly cycle that counts missed payments and emails members who are behind. Nobody clicks anything for it to run; staff can also remind people by hand.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Run the monthly counter and reminder jobs | nobody (scheduled) | `src/main/java/io/github/membertracker/scheduler/PaymentReminderScheduler.java` |
| See who is overdue | VOLUNTEER | Payment filter "Overdue" on `/members` (`frontend/src/views/MembersView.vue`), overdue list on `/dashboard` ([dashboard.md](dashboard.md)) |
| See the reminder message and its deliveries | VOLUNTEER | `/communications` ([communications.md](communications.md)) |
| Email all overdue members by hand | STAFF | "Overdue Members" on `/communications`, `POST /api/communications/send-to-overdue/{months}` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`) |
| Email one overdue member by hand | STAFF | Send Reminder on `/dashboard`, `POST /api/communications/send-to-member/{memberId}` (`CommunicationController.java`) |
| Change when or how the jobs run | developer | cron expressions and `app.payment.reminder.months-threshold` (`PaymentReminderScheduler.java`) |

There is no screen to start, stop or inspect the jobs. Role details of the screens: [members.md](members.md), [communications.md](communications.md), [dashboard.md](dashboard.md).

## How it works
### The monthly cycle
1. On the 1st of each month at 06:00 (server time zone) the counter job runs (`PaymentReminderScheduler.java`).
2. It looks at the previous month and takes every ACTIVE member (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java`).
3. It skips members who joined after the end of that month; a member with no join date is not skipped (`UpdateMissingPaymentCountersUseCase.java`).
4. It skips members who have a payment recorded for that month (`UpdateMissingPaymentCountersUseCase.java`, `src/main/java/io/github/membertracker/usecase/HasPaymentForMonthUseCase.java`).
5. For everyone else it raises the missed-months counter (`consecutiveMonthsMissed`) by 1 and saves (`UpdateMissingPaymentCountersUseCase.java`).
6. At 09:00 the reminder job runs (`PaymentReminderScheduler.java`). It takes members whose counter is at or above the threshold and who are ACTIVE (`src/main/java/io/github/membertracker/usecase/SendPaymentRemindersUseCase.java`). Sending then leaves out members without an email and sends one message per address (`Recipients.reachable`, applied in `SendCommunicationToMembersUseCase`).
7. Threshold: `app.payment.reminder.months-threshold`, 3 by default, 2 under the `dev` profile (`src/main/resources/application.properties`, `src/main/resources/application-dev.properties`).
8. If nobody qualifies, nothing is sent and nothing is stored (`SendPaymentRemindersUseCase.java`).
9. Otherwise one message titled "Payment Reminder", type REMINDER, is created for all of them: "Dear {{member_name}}, this is a friendly reminder that your membership payment is overdue. Please contact us at your earliest convenience." (`SendPaymentRemindersUseCase.java`). Each email carries the member's own name ([communications.md](communications.md#what-happens-after-send)).
10. Sending, retries, statuses and the 100 ms pause between emails are the same as for manual sends ([communications.md](communications.md#what-happens-after-send)). The members are read by status (MEMBER) and their address comes from the linked person row; the old `member.active` column no longer exists (step 12). Mail settings: [../email.md](../email.md).

Example with the default threshold 3, for a member who paid through September and then stops: 1 Nov counter 1 (October missed), 1 Dec counter 2, 1 Jan counter 3 and the first reminder at 09:00; the reminder repeats every 1st until they pay.

### The counter job runs twice in one month
1. The counter is raised once per member per month: the member remembers the last month counted (`last_missed_count_month`) and refuses to count it again (`src/main/java/io/github/membertracker/domain/model/Member.java`, `src/main/resources/db/sql/005.add-member-last-missed-count-month.sql`).
2. The second run changes nothing. The reminder job has no such protection: a second run emails again (see Rules).

### Paying clears the counter
1. When staff record a payment whose month is the current month, the counter goes to 0 (`Member.java`, `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java`).
2. A payment for an earlier month (back-dated) updates the last payment date but leaves the counter alone (`Member.java`). The member stays overdue and keeps getting reminders until a current-month payment is recorded.
3. The next monthly run only counts a month the member did not pay. See [payments.md](payments.md).

### Reactivating clears the counter
1. Switching an inactive member to active (Members screen, status button or edit form) resets the counter to 0 (`Member.java`, `src/main/java/io/github/membertracker/usecase/UpdateMemberUseCase.java`). See [members.md](members.md#mark-inactive-or-active).
2. Deactivating does not change the counter. Inactive members are not counted and not reminded by the jobs.

### What the admin sees
1. Members screen: payment badge "N months overdue", and the "Overdue" filter shows members with a counter above 0 (`frontend/src/utils/memberFilters.js`, `MembersView.vue`).
2. Dashboard: the Overdue Members card and list, with a Send Reminder button for STAFF+ ([dashboard.md](dashboard.md)).
3. Communications: the "Payment Reminder" message appears in the list with the date the job ran, and its deliveries (one per overdue member, with SENT or FAILED status) in the dialog.
4. The jobs write to the application log only ("Starting payment reminder process", errors) (`PaymentReminderScheduler.java`).

### Manual alternatives
1. Send to overdue: on Communications, choose Overdue Members and a number of months; it emails the active members whose counter is at or above that number (nobody matching is a 400 and nothing is stored) ([communications.md](communications.md#send-to-members-behind-by-n-months-staff)).
2. Send Reminder: on the Dashboard, one click emails one overdue member a fixed text ([dashboard.md](dashboard.md#send-a-reminder-to-one-overdue-member-staff)).
3. Neither changes any counter, and neither is limited by the threshold.

## Rules
- The counter job counts only ACTIVE members, only the previous month, once per member per month (`UpdateMissingPaymentCountersUseCase.java`).
- A member who joined after the end of the counted month is not counted for it.
- Reminders go to ACTIVE members with a counter at or above the threshold, every month, until they pay: there is no "already reminded" state (`SendPaymentRemindersUseCase.java`).
- The reminder threshold is unrelated to the 3-month rule of automatic deactivation, which does not exist (see Known issues).
- A run missed because the application was down at 06:00 or 09:00 on the 1st is not repeated: that month is never counted, and the next run counts only the next month (`PaymentReminderScheduler.java`; [payment-reminder-scheduler.md](payment-reminder-scheduler.md#errors)).
- One application instance is assumed. With two running, both fire the jobs: the counter job is safe (once per month), the reminder job would email every overdue member twice.
- A failure in either job is logged and swallowed; nothing is retried (`PaymentReminderScheduler.java`).
- Scheduling is switched on in `src/main/java/io/github/membertracker/infrastructure/config/SchedulingConfig.java`.

## Known issues
- **Reminder window and auto-deactivation never ran:** the pre-due reminder window (last 7 days of the month) and automatic deactivation (3 or more missed months) lived in `DefaultMembershipPolicy`, reachable only through the unused `ProcessMemberPaymentUseCase`. The code was removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history; decide whether to build them for real. So there is no pre-due-date reminder and members are never deactivated automatically, however many months they miss. Logged in [../todo.md](../todo.md).
- Members are reminded every month at or above the threshold, with no cap and no "reminded already" record.
- Overdue members are not told how many months they owe: the text is fixed.
- A missed run is lost (see Rules); there is no catch-up and no admin screen showing when the jobs last ran.
- The counter is raised only by the monthly job (`Member.markMissedFor`); the unused `Member.markPaymentMissed` was removed in `chore: remove unused domain methods`.
- Paying an earlier month does not clear the counter, so a member who catches up on old months stays overdue until they pay the current month (`Member.java`).
- Single instance only; no lock between instances.

## Related
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md): jobs, cron, collaborators
- [communications.md](communications.md): how the reminder email is sent and tracked
- [members.md](members.md), [payments.md](payments.md), [dashboard.md](dashboard.md)
- [../email.md](../email.md), [../architecture.md](../architecture.md)
