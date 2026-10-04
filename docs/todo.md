# MemberTracker Project - Todo List

*Last checked against the code: 4 October 2026.*

This is the live backlog: only open work is listed. What was fixed, and in which commit, is in the Status section of [functionality-audit.md](functionality-audit.md#status-4-october-2026) and in `git log`. Doc index: [../README.md](../README.md).

Done and removed from this list (October 2026): auth hardening (token types, refresh cookie path, one login error, unlock after 15 minutes, per-IP and per-email throttle, password change ends other sessions, session renewal), CSRF, secrets out of config, HTTPS through Caddy with security headers, roles and route guards, activity log with the administrator screen, payment rules (back-dating, no payments for non-active members, duplicate check), send rules (recipients must exist, reminders as reminders, `{{member_name}}`), staff-only exports, archive instead of delete, member status, the empty `person` and `household` tables, optional and shared email, delivery attempts and retry, the Tailwind redesign (Bootstrap removed), and the QA fixes M1 to M3 and L1 to L5.

## (a) Next structural work

[person-membership-plan.md](person-membership-plan.md), Phase 4: steps 1 to 11 are done and step 12 is done in code (migration 014 waits for the owner to run it on the demo database).

- [x] Restart the demo backend on the new build so migration 013 runs on `felege_selam` and step 9 starts reading `person` (done 4 October 2026: 11 members, 11 people, drift 0)
- [x] Step 10: households (API and UI)
- [x] Step 11, API: people without a membership (`/api/people`, start a membership, drift query adjusted; see [features/people.md](features/people.md))
- [x] Step 11, UI: dependents on the household detail (list, add, edit, make a member, delete); checked in a browser 4 October 2026 (add, make a member); edit, delete and the VOLUNTEER view not yet
- [x] Step 12: contract, the destructive step that drops the legacy columns (`name`, `email`, `phone`, `active` on `member`): migration `014`, dual-write and `active` removed; not yet applied to the demo database (run the section 6 dry run first)

## (b) Small leftovers

- [ ] Signed-out load makes one 401 and one 400 probe. Kept on purpose: it is how an expired access cookie with a valid refresh cookie signs the user back in.
- [x] Members CSV has technical headers and a redundant `active` column: now `ID,Name,Email,Phone,Join date,Months behind,Status`
- [x] Dashboard revenue-by-month chart: there is no chart library and the dashboard shows one monthly figure only — added "Collected by month", 12 CSS bars from `GET /api/dashboard/collected-by-month`, documented in `docs/features/dashboard.md`
- [x] Coverage reports: no JaCoCo in `build.gradle` and no coverage script for the frontend — added JaCoCo (`jacocoTestReport`, xml + html, no threshold) and `npm run coverage` (Vitest + `@vitest/coverage-v8`), documented in `docs/development.md`
- [ ] My dues match is by email; an explicit user-to-member link is the follow-up if emails turn out to be unreliable
- [ ] Activity-log retention job: entries accumulate forever (see [features/activity.md](features/activity.md))
- [ ] A durable send queue and an automatic later retry of FAILED deliveries (the cached thread pool loses unsent mail on a restart; only the manual Retry exists)
- [ ] Server-side search and pagination: all lists load every row; deferred until a congregation above about 2,000 members or several campuses

## (c) Needs a decision or an account

- [ ] API timestamps (`createdDate`, `archivedAt`, etc.) are server-local `LocalDateTime` while MySQL stores UTC (JDBC `serverTimezone=UTC`), so they look 2 hours apart in CEST; decide on UTC everywhere or `Instant`, and check the monthly counter job's month boundary
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
