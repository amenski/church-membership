# PaymentReminderScheduler

`src/main/java/io/github/membertracker/scheduler/PaymentReminderScheduler.java`

Two monthly cron jobs (no HTTP surface) that raise members' missed-payment counters and email overdue members. Scheduling is switched on by `infrastructure/config/SchedulingConfig.java` (`@EnableScheduling`).

## Actions
| Method | Cron | Effective schedule | Does |
|---|---|---|---|
| `updateMissingPaymentCounters` | `0 0 6 1 * ?` | the 1st of every month, 06:00, server time zone | calls `UpdateMissingPaymentCountersUseCase.invoke()`; counts the previous month as missed for MEMBER-status members with no payment for it |
| `sendPaymentReminders` | `0 0 9 1 * ?` | the 1st of every month, 09:00, server time zone (after the counters) | calls `SendPaymentRemindersUseCase.invoke(monthsThreshold)`; emails every MEMBER-status member with `consecutiveMonthsMissed >= monthsThreshold` |

- Threshold: `app.payment.reminder.months-threshold`, injected through the constructor (`PaymentReminderScheduler.java`). Default 3 (`application.properties`), 2 under the `dev` profile (`application-dev.properties`); the code fallback is 3.
- Counter job: `previousMonth = YearMonth.now(clock).minusMonths(1)`; loads `memberRepository.findDuesPaying()` (status MEMBER); skips members whose `joinDate` is after the end of `previousMonth` (a missing join date is counted); skips members with a payment for `previousMonth`; for the rest calls `Member.markMissedFor(previousMonth)` and saves only when it returned true (`UpdateMissingPaymentCountersUseCase.java`).
- Idempotent: `Member.markMissedFor` (`domain/model/Member.java`) raises the counter once per month, remembering the month in `lastMissedCountMonth`, stored in `member.last_missed_count_month` (Liquibase changeset `005.add-member-last-missed-count-month.sql`). Running the job again in the same month changes nothing.
- Reminder job: one `Communication` ("Payment Reminder", type `REMINDER`) to the overdue MEMBER-status members, channel `EMAIL` (`SendPaymentRemindersUseCase.java`). Returns `null` when nobody qualifies.

## Collaborators
- `UpdateMissingPaymentCountersUseCase` -> `MemberRepository.findDuesPaying`/`save`, `HasPaymentForMonthUseCase` (`UpdateMissingPaymentCountersUseCase.java`; a second constructor takes a `Clock`, used by tests)
- `SendPaymentRemindersUseCase` -> `MemberRepository.findByConsecutiveMonthsMissedGreaterThanEqual` (`SendPaymentRemindersUseCase.java`), `SendCommunicationToMembersUseCase.invoke`
- `SendCommunicationToMembersUseCase` -> saves the `Communication` with `PENDING` deliveries, then sends async via `EmailService` and saves each delivery's result through `MessageDeliveryRepository` (`SendCommunicationToMembersUseCase.java`)
- Both reminder use cases are wired as beans in `infrastructure/config/UseCaseConfig.java`
- Mail config, retries, delivery tracking: [email.md](../email.md)

## Errors
- Each job wraps its call in `try/catch (Exception)` and logs `error` (`PaymentReminderScheduler.java`); nothing is rethrown or retried.
- If the application is down at 06:00 or 09:00 on the 1st, that run is skipped: there is no catch-up. Missing the counter run means members are not counted for that month; the next month's run counts only the next month.
- Per-recipient email failures are handled inside `SendCommunicationToMembersUseCase`, not by the scheduler: delivery marked `FAILED`, loop continues (`SendCommunicationToMembersUseCase.java`). See [email.md](../email.md).

## Side effects
- Counter job: updates `consecutiveMonthsMissed` and `lastMissedCountMonth` on member rows.
- Reminder job: inserts a `Communication` and one `MessageDelivery` per overdue member (PENDING, then SENT or FAILED as each email result comes in); sends emails on a background thread, so the job's "Successfully sent ... to N members" log (N is the number of deliveries) fires before delivery finishes.
- Counter reset happens elsewhere: `Member.recordPayment` sets it to 0 when the payment covers the current period (`domain/model/Member.java`). `lastMissedCountMonth` is not reset, so a member who pays and misses a later month is counted again for that later month only.

## Gotchas
- A single application instance is assumed: with two instances both would run the jobs, and reminders would be sent twice (the counter job is safe to run twice, the reminder job is not).
- Members whose status is not MEMBER (inactive, deceased, transferred, archived) are skipped by both jobs, and members who joined after the counted month are not counted for it.
- The reminder text is stored once with the `{{member_name}}` placeholder (`SendPaymentRemindersUseCase.java`); each recipient's name is filled in at send time by `MessageTemplates.personalize` (`SendCommunicationToMembersUseCase.java`). See [email.md](../email.md#personalisation).
- Members at or over the threshold are reminded every month until they pay (no "already reminded" state).
- There is no pre-due reminder window and no automatic deactivation: the policy code for both never ran and was removed in `chore: remove unused use cases, the membership policy and PhoneNumber` (recover from git history; decide whether to build it for real).
