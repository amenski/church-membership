# MemberTracker Project - Todo List

*Last checked against the code: 4 October 2026.*

This is the live backlog: only open work is listed. What was fixed, and in which commit, is in the Status section of [functionality-audit.md](functionality-audit.md#status-4-october-2026) and in `git log`. Doc index: [../README.md](../README.md).

Done and removed from this list (October 2026): auth hardening (token types, refresh cookie path, one login error, unlock after 15 minutes, per-IP and per-email throttle, password change ends other sessions, session renewal), CSRF, secrets out of config, HTTPS through Caddy with security headers, roles and route guards, activity log with the administrator screen, payment rules (back-dating, no payments for non-active members, duplicate check), send rules (recipients must exist, reminders as reminders, `{{member_name}}`), staff-only exports, archive instead of delete, member status, optional and shared email, delivery attempts and retry, the Tailwind redesign (Bootstrap removed), and the QA fixes M1 to M3 and L1 to L5.

## (a) Next structural work

[person-membership-plan.md](person-membership-plan.md), Phase 4: steps 1 to 6 are done; steps 7 to 12 are open and run in this order.

- [ ] Step 7: create the `person` and `household` tables, unused
- [ ] Step 8: backfill `person` and dual-write
- [ ] Step 9: read name, email and phone from `person`
- [ ] Step 10: households (API and UI)
- [ ] Step 11: people without a membership (children, dependents)
- [ ] Step 12: contract, the destructive step that drops the legacy columns (`name`, `email`, `phone`, `active` on `member`)

## (b) Small leftovers

- [ ] Signed-out load makes one 401 and one 400 probe. Kept on purpose: it is how an expired access cookie with a valid refresh cookie signs the user back in.
- [ ] `frontend/src/services/api.js` has two `getOverdueMembers` keys (`/members/overdue/{months}` and `/dashboard/overdue-members`); the second silently replaces the first
- [ ] The activity log is noisy: a status change writes two entries (`MEMBER_UPDATED` and the typed one, `UpdateMemberUseCase.java:60` and `:64`)
- [ ] Some error and activity descriptions show raw text: the period as `2026-10` and enum names such as `TRANSFERRED`
- [ ] Members CSV has technical headers (`joinDate`, `consecutiveMonthsMissed`) and a redundant `active` column next to `status` (`MemberController.java:181`)
- [ ] The member list prints a blank line when a member has no email (`MembersView.vue:104`, `:132`)
- [ ] Date inputs use the browser's locale format (`type="date"`)
- [ ] Dashboard revenue-by-month chart: there is no chart library and the dashboard shows one monthly figure only
- [ ] Coverage reports: no JaCoCo in `build.gradle` and no coverage script for the frontend
- [ ] Activity-log retention job: entries accumulate forever (see [features/activity.md](features/activity.md))
- [ ] A durable send queue and an automatic later retry of FAILED deliveries (the cached thread pool loses unsent mail on a restart; only the manual Retry exists)
- [ ] Server-side search and pagination: all lists load every row; deferred until a congregation above about 2,000 members or several campuses
- [ ] Broken Gradle tasks `:frontend:vueRunDev` and `:frontend:vueLint` call `npm run serve` and `npm run lint`, which are not in `package.json`

## (c) Needs a decision or an account

- [ ] SMS and WhatsApp (a provider account such as Twilio; today those channels are stubs that mark every delivery failed)
- [ ] Calendar integration
- [ ] Online payment gateway
- [ ] Mobile application
- [ ] Reminder policy: there is no pre-due reminder window and no automatic deactivation after missed months (the old policy code was removed; recover it from git history if you decide to build it)

## (d) Deferred by the user

- [ ] Docker and Docker Compose
- [ ] CI pipeline, and Testcontainers for database tests
- [ ] Monitoring: health checks, structured (JSON) logging, error tracking
- [ ] Backup and recovery procedures
