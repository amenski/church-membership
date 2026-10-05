# MemberTracker Project - Todo List

*Last checked against the code: 5 October 2026.*

This is the live backlog: only open work is listed. What was fixed, and in which commit, is in the Status section of [functionality-audit.md](functionality-audit.md#status-5-october-2026) and in `git log`. The finished plans (person and household model, role checkpoints, validation, design plan and gap list) are in [archive/](archive/). Doc index: [../README.md](../README.md).

Larger roadmap items that nobody has started (import, user management, password reset, events, attendance, giving funds) are in the roadmap of [functionality-audit.md](functionality-audit.md); they are not repeated here.

## (a) Needs a decision

- [ ] Activity-log retention: how long to keep entries before a job deletes them (today they accumulate forever, see [features/activity.md](features/activity.md))
- [ ] Reminder policy: a pre-due window, automatic deactivation after missed months, or neither (there is no pre-due window and no automatic deactivation today; the old policy code was removed, recover it from git history if you decide to build it; see [features/payment-reminders.md](features/payment-reminders.md))
- [ ] API timestamps (`createdDate`, `archivedAt`, etc.) are server-local `LocalDateTime` while MySQL stores UTC (JDBC `serverTimezone=UTC`), so they look 2 hours apart in CEST; decide on UTC everywhere or `Instant`, and check the monthly counter job's month boundary
- [ ] A second name field for Amharic names (the data has one name field, so the member page cannot show an Amharic name above the Latin one)
- [ ] A monthly dues amount setting (the Overview cannot say "$100 of $250 expected" without one)
- [ ] SMS and WhatsApp (needs a provider account such as Twilio; today those channels are stubs that mark every delivery failed)
- [ ] Calendar integration
- [ ] Online payment gateway
- [ ] Mobile application

## (b) Small leftovers (each checked against the code on 5 October 2026)

- [ ] A signed-out load makes one 401 and one 400 probe. Kept on purpose: it is how an expired access cookie with a valid refresh cookie signs the user back in.
- [ ] A durable send queue and an automatic later retry of FAILED deliveries (`SendCommunicationToAllMembersUseCase` and `SendCommunicationToMembersUseCase` each use a cached thread pool, so unsent mail is lost on a restart; only the manual Retry exists)
- [ ] The Activity "Show more" stops at 200 entries (`@Max(200)` on `GET /api/activity-log`, `MAX_LIMIT` in `ActivityView.vue`); older entries cannot be reached
- [ ] Send message from the Members selection bar links to `/communications` without the selected members; Messages reads no member parameter, so the recipients are chosen again
- [ ] The Payments phone list cannot change its sort: the sortable headers exist from `md` only, and a phone keeps whatever sort is in the URL
- [ ] The Overview ledger "See all" link opens `/members?dues=behind`, which lists members who are behind; the ledger count also includes members who are only due this month, so the list can be shorter than the count
- [ ] The member page has no History card: `GET /api/activity-log` is ADMIN only and has no per-member filter
- [ ] Components and views with no test: `MemberDetailView`, the `MembersView` selection bar (bulk bar), the `CommunicationsView` recipient preview and the `MemberPicker` component
- [ ] My dues finds the member by email; an explicit user-to-member link is the follow-up if emails turn out to be unreliable (see [architecture.md](architecture.md#decisions))
- [ ] Members, Households and Messages still load every row and page in the browser (Payments pages on the server); move them to the server above about 2,000 members or with several campuses

## (c) Deferred by the owner

- [ ] Docker for the app: a Dockerfile and an app image (the MySQL database already has `docker-compose.yml`, see [development.md](development.md#mysql-with-docker-compose))
- [ ] CI pipeline, and Testcontainers for database tests
- [ ] Monitoring: health checks, structured (JSON) logging, error tracking
- [ ] Backup and recovery procedures
