# PaymentReminderScheduler

`src/main/java/io/github/membertracker/scheduler/PaymentReminderScheduler.java`

**Jobs do not run today.** No `@EnableScheduling` exists anywhere in `src/main`, so the `@Scheduled` methods are never triggered (audit C3).

Two daily cron jobs (no HTTP surface) that bump members' missed-payment counters and email overdue members.

## Actions
| Method | Cron | Effective schedule | Does |
|---|---|---|---|
| `updateMissingPaymentCounters` (`:30`) | `0 0 6 * * ?` (`:29`) | every day 06:00, server time zone | calls `UpdateMissingPaymentCountersUseCase.invoke()`; increments `consecutiveMonthsMissed` and saves, per member with no payment for the previous month |
| `sendPaymentReminders` (`:44`) | `0 0 9 * * ?` (`:43`) | every day 09:00, server time zone | calls `SendPaymentRemindersUseCase.invoke(2)` (`:47`); emails every member with `consecutiveMonthsMissed >= 2` |

- Counter job: `YearMonth.now().minusMonths(1)`, loops `memberRepository.findAll()` (`UpdateMissingPaymentCountersUseCase.java:25-34`).
- Reminder job: one `Communication` ("Payment Reminder", type `REMINDER`) to all overdue members, channel `EMAIL` (`SendPaymentRemindersUseCase.java:29-37`). Returns `null` when nobody is overdue.

## Collaborators
- `UpdateMissingPaymentCountersUseCase` -> `MemberRepository.findAll`/`save`, `HasPaymentForMonthUseCase` (`UpdateMissingPaymentCountersUseCase.java:14`)
- `SendPaymentRemindersUseCase` -> `MemberRepository.findByConsecutiveMonthsMissedGreaterThanEqual` (`SendPaymentRemindersUseCase.java:29`), `SendCommunicationToMembersUseCase.invoke` (`:37`)
- `SendCommunicationToMembersUseCase` -> saves the `Communication` with `PENDING` deliveries, then sends async via `EmailService` (`SendCommunicationToMembersUseCase.java:39-67`, `:70`)
- Both reminder use cases are wired as beans in `infrastructure/config/UseCaseConfig.java:197`, `:202`
- `MembershipPolicy`/`DefaultMembershipPolicy`: not used here (see Gotchas)
- Mail config, retries, delivery tracking: [email.md](../email.md)

## Errors
- Each job wraps its call in `try/catch (Exception)` and logs `error` (`PaymentReminderScheduler.java:35-37`, `:54-56`); nothing is rethrown or retried until the next day.
- Per-recipient email failures are handled inside `SendCommunicationToMembersUseCase`, not by the scheduler: delivery marked `FAILED`, loop continues (`SendCommunicationToMembersUseCase.java:88-94`). See [email.md](../email.md).

## Side effects
- Counter job: updates `consecutiveMonthsMissed` on member rows.
- Reminder job: inserts a `Communication` plus one `MessageDelivery` per overdue member; sends emails on a background thread, so the job's "Successfully sent" log (`:49`) fires before delivery finishes.
- Counter reset happens elsewhere: `Member.recordPayment` sets it to 0 when the payment covers the current period (`domain/model/Member.java:51-53`).

## Gotchas
- Not scheduled: no `@EnableScheduling` in `src/main` (audit C3).
- Counter increments on every run, not once per month: if enabled, a member missing last month gains about 30 per month (audit C3). Pinned by `@Disabled` test `src/test/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCaseTest.java:100-101`.
- Reminder body contains the literal `{{member_name}}`; nothing substitutes it (`SendPaymentRemindersUseCase.java:34`, audit C4). Pinned by `@Disabled` test `src/test/java/io/github/membertracker/usecase/SendPaymentRemindersUseCaseTest.java:64-65`.
- Reminders repeat daily at 09:00 to the same members for as long as their counter is >= 2 (no "already reminded" state), and the counter keeps growing (audit C3).
- Neither job filters on `isActive`: counter uses `findAll` (`UpdateMissingPaymentCountersUseCase.java:27`); reminder query is by counter only (`SendPaymentRemindersUseCase.java:29`).
- The reminder window in `DefaultMembershipPolicy.shouldSendReminder` (last 7 days of the month, `DefaultMembershipPolicy.java:18`, `:30-43`) is not consulted by the scheduler; only `ProcessMemberPaymentUseCase` calls it (`usecase/ProcessMemberPaymentUseCase.java:104`).
- Threshold `2` is a literal in the scheduler (`PaymentReminderScheduler.java:47`), unrelated to `MAX_CONSECUTIVE_MISSED_MONTHS = 3` (`DefaultMembershipPolicy.java:17`).
- `ProcessMemberPaymentUseCase` (the only caller of `DefaultMembershipPolicy.shouldSendReminder`) is itself never invoked by any entry point (it is only a bean in `UseCaseConfig`), so neither the reminder window nor the automatic deactivation rule runs.
