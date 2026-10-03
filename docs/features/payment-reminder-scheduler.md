# PaymentReminderScheduler

`src/main/java/io/github/membertracker/scheduler/PaymentReminderScheduler.java`

Two monthly cron jobs (no HTTP surface) that raise members' missed-payment counters and email overdue members. Scheduling is switched on by `infrastructure/config/SchedulingConfig.java` (`@EnableScheduling`).

## Actions
| Method | Cron | Effective schedule | Does |
|---|---|---|---|
| `updateMissingPaymentCounters` (`:34`) | `0 0 6 1 * ?` (`:33`) | the 1st of every month, 06:00, server time zone | calls `UpdateMissingPaymentCountersUseCase.invoke()`; counts the previous month as missed for active members with no payment for it |
| `sendPaymentReminders` (`:49`) | `0 0 9 1 * ?` (`:48`) | the 1st of every month, 09:00, server time zone (after the counters) | calls `SendPaymentRemindersUseCase.invoke(monthsThreshold)` (`:52`); emails every active member with `consecutiveMonthsMissed >= monthsThreshold` |

- Threshold: `app.payment.reminder.months-threshold`, injected through the constructor (`PaymentReminderScheduler.java:22-23`). Default 3 (`application.properties`), 2 under the `dev` profile (`application-dev.properties`); the code fallback is 3.
- Counter job: `previousMonth = YearMonth.now(clock).minusMonths(1)`; loads `memberRepository.findByActive(true)`; skips members whose `joinDate` is after the end of `previousMonth` (a missing join date is counted); skips members with a payment for `previousMonth`; for the rest calls `Member.markMissedFor(previousMonth)` and saves only when it returned true (`UpdateMissingPaymentCountersUseCase.java:34-49`).
- Idempotent: `Member.markMissedFor` (`domain/model/Member.java:69`) raises the counter once per month, remembering the month in `lastMissedCountMonth`, stored in `member.last_missed_count_month` (Liquibase changeset `005.add-member-last-missed-count-month.sql`). Running the job again in the same month changes nothing.
- Reminder job: one `Communication` ("Payment Reminder", type `REMINDER`) to the overdue members that are active, channel `EMAIL` (`SendPaymentRemindersUseCase.java:29-40`). Returns `null` when nobody qualifies.

## Collaborators
- `UpdateMissingPaymentCountersUseCase` -> `MemberRepository.findByActive`/`save`, `HasPaymentForMonthUseCase` (`UpdateMissingPaymentCountersUseCase.java:21`; a second constructor takes a `Clock`, used by tests)
- `SendPaymentRemindersUseCase` -> `MemberRepository.findByConsecutiveMonthsMissedGreaterThanEqual` (`SendPaymentRemindersUseCase.java:30`), `SendCommunicationToMembersUseCase.invoke` (`:40`)
- `SendCommunicationToMembersUseCase` -> saves the `Communication` with `PENDING` deliveries, then sends async via `EmailService` (`SendCommunicationToMembersUseCase.java:40-68`, `:71`)
- Both reminder use cases are wired as beans in `infrastructure/config/UseCaseConfig.java:203`, `:208`
- `MembershipPolicy`/`DefaultMembershipPolicy`: not used here (see Gotchas)
- Mail config, retries, delivery tracking: [email.md](../email.md)

## Errors
- Each job wraps its call in `try/catch (Exception)` and logs `error` (`PaymentReminderScheduler.java:39-41`, `:59-61`); nothing is rethrown or retried.
- If the application is down at 06:00 or 09:00 on the 1st, that run is skipped: there is no catch-up. Missing the counter run means members are not counted for that month; the next month's run counts only the next month.
- Per-recipient email failures are handled inside `SendCommunicationToMembersUseCase`, not by the scheduler: delivery marked `FAILED`, loop continues (`SendCommunicationToMembersUseCase.java:89-95`). See [email.md](../email.md).

## Side effects
- Counter job: updates `consecutiveMonthsMissed` and `lastMissedCountMonth` on member rows.
- Reminder job: inserts a `Communication` and is meant to insert one `MessageDelivery` per overdue member, but deliveries are currently not persisted (see [communications.md](communications.md#known-issues)); sends emails on a background thread, so the job's "Successfully sent" log (`:54`) fires before delivery finishes.
- Counter reset happens elsewhere: `Member.recordPayment` sets it to 0 when the payment covers the current period (`domain/model/Member.java:52-54`). `lastMissedCountMonth` is not reset, so a member who pays and misses a later month is counted again for that later month only.

## Gotchas
- A single application instance is assumed: with two instances both would run the jobs, and reminders would be sent twice (the counter job is safe to run twice, the reminder job is not).
- Inactive members are skipped by both jobs, and members who joined after the counted month are not counted for it.
- The reminder text is stored once with the `{{member_name}}` placeholder (`SendPaymentRemindersUseCase.java:37`); each recipient's name is filled in at send time by `MessageTemplates.personalize` (`SendCommunicationToMembersUseCase.java:77-78`). See [email.md](../email.md#personalisation).
- Members at or over the threshold are reminded every month until they pay (no "already reminded" state).
- The reminder window in `DefaultMembershipPolicy.shouldSendReminder` (last 7 days of the month, `DefaultMembershipPolicy.java:30-43`) is not consulted by the scheduler; only `ProcessMemberPaymentUseCase` calls it (`usecase/ProcessMemberPaymentUseCase.java:104`).
- The reminder threshold is unrelated to `MAX_CONSECUTIVE_MISSED_MONTHS = 3` (`DefaultMembershipPolicy.java:17`), the automatic-deactivation rule.
- `ProcessMemberPaymentUseCase` (the only caller of `DefaultMembershipPolicy.shouldSendReminder`) is itself never invoked by any entry point (it is only a bean in `UseCaseConfig`), so neither the reminder window nor the automatic deactivation rule runs.
