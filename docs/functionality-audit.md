# MemberTracker Functionality Audit

*Audited: 3 October 2026. Status section last checked against the code: 5 October 2026.*
*Branch: `feature/role-auth` @ `b117be4` plus working tree*
*Method: source reading only. The app was not run, so no finding is confirmed at runtime.*

## Status (5 October 2026)

The findings below are kept as written at audit time. This table says where each stands now. Open work is tracked in [todo.md](todo.md).

| Finding | State | Fixed in |
|---------|-------|----------|
| C1 Every screen returns 403 | Done | `3cf5d84`, `14335ff` |
| C2 Payment overwrites the member | Done | `755f151` |
| C3 Reminders never run, would spam | Done | `a45c603` |
| C4 `{{member_name}}` not filled in | Done | `1ab715c` |
| C5 Sessions end after 30 minutes, token types | Done | `5f809bb`, `c36f8bc` |
| C6 Login reveals which emails exist | Done | `1b812e1` |
| C7 Payment delete deletes nothing | Done (endpoint removed) | `2d0383f` |
| C8 Member edit accepts system fields | Done | `605e51a` |
| C9 Deleting a member erases payments | Done (archive; foreign keys RESTRICT) | `abc6164` |
| C10 Email required and unique, no household | Done (email optional and shared, households, people without a membership; legacy member columns dropped in migration `014`) | `9cc0d61`, `bfdf6b4`, `87892f0`, `d637705`; plan in [archive/person-membership-plan.md](archive/person-membership-plan.md) |
| CSRF off, default secrets and credentials | Done | `d4c5c0b`, `aff5705`, `5efd8cf`, `db563d5` |
| No HTTPS configuration | Done (Caddy, security headers) | `723c489`, `c107b09` |
| Lockout permanent, login throttling | Done | `6398240` |
| Password change keeps other sessions | Done | `41dfce5` |
| No audit trail (`activity_log` unused) | Done; retention job open | `1f90665`, `24d2236` |
| Exports open to volunteers | Done (STAFF and above) | `2ae3919`, `342dbc6` |
| Payments limited to the last 3 months | Done (any month, paid-on date) | `2ae3919`, `5bbbfdc` |
| Dashboard hides errors, counts all members | Done | `7ec4f94` |
| Communication deliveries not stored, no retry | Done (attempts counted, manual retry) | `ec1be9c`, `884e918`, `b460af5` |
| Member status is one boolean | Done (five statuses) | `f9accb8`, `9074602`, `472a30a` |
| Search and filters | Done. Payments page, search and sort on the server (`GET /api/payments/page`); Members, Households and Messages page in the browser | `290a026`, `1553827`, `6598724`, `a821efa` |
| Frontend routes ignore roles | Done | `14335ff` |
| Bootstrap, plain UI | Done (Tailwind redesign) | `2b079ee`, `e789ca1` |
| Households and dependents (people without a membership) | Done | `bfdf6b4`, `87892f0`, `b370f41` |
| Member self-service ("My dues") | Done (matched to the member by email) | `09508f6` |
| Visitors, events, attendance, groups | Open | none; Phases 1 to 4 below |
| Import, user management (there is no admin API to create or disable users), password reset, MFA | Open | none |
| SMS and WhatsApp stubs, durable send queue | Open | none |
| Giving funds, `BigDecimal` money (amounts are still `Double`), receipts and statements | Open | none |
| Multi-campus field, configurable CORS (the allowed origins are fixed in `WebMvcConfig`) | Open | none |
| Docker for the app, CI, monitoring, backups | Deferred by the owner; the MySQL compose file is done | `a44c17a` |

## Verdict

**Maturity: 1 out of 5 (ad-hoc).** The average across the 17 areas below is 0.6. The headline score of 1.0 gives credit for the clean architecture: domain, use cases and repositories are separate, which makes the roadmap cheaper than a rewrite.

The system stores a flat list of members, records monthly dues, and emails either all members or overdue members. It has no households, visitors, events, attendance, groups, giving funds, member self-service, search, import or audit trail. Fix the ten defects in section 3 before building anything new. Three of them stop staff from using the app at all.

## 1. Maturity by area

Scale: **0** absent · **1** stub or flat data · **2** basic, usable · **3** functional for one church · **4** managed, with workflow · **5** complete platform

### People

| Area | Score | What exists today, and what is missing |
|------|:-----:|----------------------------------------|
| Member profiles | 1 | Name, email, phone, join date, active flag. No address, birth date, photo, marital status, sacraments or baptism record, notes, or custom fields. Email is required and unique, so children, elders without email, and spouses who share an inbox cannot be stored. |
| Households | 0 | No family or household entity, relationships, or head of household. Giving, mailing and the directory all depend on it. |
| Visitor tracking | 0 | No guest record, first-visit date, follow-up assignment, or path from visitor to member. Membership status is a single boolean. |

### Access

| Area | Score | What exists today, and what is missing |
|------|:-----:|----------------------------------------|
| User accounts | 1 | Self-registration is disabled and there is no admin API to create, invite, unlock (failure locks end by themselves after 15 minutes) or disable users, so accounts can only be added in SQL. A user account is not linked to a member record. No password reset. |
| Roles and permissions | 1 | Four roles (Member, Volunteer, Staff, Admin) exist as an enum, but there is no role hierarchy, and 16 endpoints still require the removed `USER` role. No scoped permissions (for example, a group leader who sees only their group), no finance role. Frontend routes never set `requiresRole`. |
| Authentication | 2 | BCrypt cost 12, httpOnly cookies, lockout after 5 failures (15 minutes), strong password rule. But refresh never reaches the server, refresh and access tokens are interchangeable, login reveals which emails exist, logout does not revoke tokens, there is no MFA. Fixed since: a lock now ends after 15 minutes (`locked_until`), locked and failed attempts answer with one generic message, sign-in is throttled per email and per IP, and the attempt counter is atomic. |

### Engagement

| Area | Score | What exists today, and what is missing |
|------|:-----:|----------------------------------------|
| Communication | 1 | Email to all members or to overdue members, with delivery status per recipient and retry with backoff. Only those two audiences. No templates. Personalisation: `{{member_name}}` is replaced per recipient. No unsubscribe or consent. SMS and WhatsApp are stubs that mark every delivery as failed. Sends run on an in-memory thread pool and are lost on restart. |
| Events and calendar | 0 | Not present. No services, feasts, meetings, registrations or room booking. |
| Attendance | 0 | Not present. No service headcount, check-in or absence follow-up. |
| Groups and ministries | 0 | Not present. No choir, Sunday school, youth or committee membership, no leaders, no volunteer rosters. |

### Finance

| Area | Score | What exists today, and what is missing |
|------|:-----:|----------------------------------------|
| Giving | 1 | Monthly dues per member only. No funds or designations, one-off gifts, pledges, campaigns, batch entry for Sunday offerings, receipts or annual statements. Amounts are `Double` in the domain. Deleting a payment does nothing. Any month up to ten years back can be entered with an optional paid-on date, so history can be loaded (one payment per member per month; no batch import). |

### Platform

| Area | Score | What exists today, and what is missing |
|------|:-----:|----------------------------------------|
| Reporting | 1 | Four dashboard counters and two recent-activity lists, computed by database queries (counts, a sum, limited recent lists). No date ranges, trends, giving by fund, attendance or growth reports. |
| Search | 0 | No search or filter parameters on any endpoint, and no pagination. Filtering happens in the browser on the full list. |
| Notifications | 0 | A monthly reminder scheduler runs on the 1st (C3 fixed); `{{member_name}}` in the reminder is filled in per recipient (C4 fixed). No in-app notifications, birthday or anniversary alerts, or staff task alerts. |
| Import and export | 1 | CSV export of members and payments. No import, so a church moving from a spreadsheet must type every record by hand. |
| Privacy | 0 | No consent records, retention rules, subject-access export or audit log. An `activity_log` table exists, but nothing writes to it. *Status (October 2026): sign-ins, member changes, payments, messages and exports are now written to it with the actor, and administrators read them at `GET /api/activity-log`; there is still no retention rule.* Exports (members, selected members, payments) are STAFF and above; volunteers cannot download the member list. Deleting a member permanently deletes their giving history. |
| Security operations | 1 | CSRF protection is off while authentication uses cookies (fixed 3 Oct 2026). A default JWT secret and `root/password` database credentials ship in `application.properties`, with `useSSL=false` and SQL logging on (fixed 3 Oct 2026: no defaults in the default profile). No HTTPS configuration. The repo has one test (context load, disabled). |

## 2. Fitness by role

What each person can actually do today:

| Role | Status | Today |
|------|--------|-------|
| Administrator | Blocked | Can create, edit and delete members, but cannot list them: the read endpoints require exactly Volunteer and there is no hierarchy. Payments, communications and the dashboard return 403. Cannot manage user accounts. |
| Staff | Blocked | Same as Admin, minus delete. Can save a member but cannot see the list it lands in. |
| Volunteer | Over-permitted | The only role that can read members, and it can no longer export CSV (STAFF and above). Cannot do anything operational. |
| Member | Empty | Can sign in and change their own password and profile. No giving history, household, directory, events or groups. |
| Visitor | Absent | No way to be recorded, welcomed or followed up. |

## 3. Critical gaps

Each of these was confirmed by reading the source on this branch. None was confirmed by running the app.

### Blocking

**C1. Every operational screen returns 403**
The role rename left `hasRole('USER')` on 16 endpoints (6 in Payment, 6 in Communication, 4 in Dashboard). There is no `RoleHierarchy` bean, so Admin and Staff fail `hasRole('VOLUNTEER')` on member reads. Seed data and the `users.role` column default still write `USER`, which `UserRole.fromCode` rejects.
`PaymentController.java` · `SecurityConfig.java` · `001.schema-creation.sql` · `002.sample-data.sql`
> **Status (3 Oct 2026):** fixed in `3cf5d84` (role hierarchy, mapped roles, migration 004, `RoleAuthorizationTest`). Frontend routes now set `requiresRole`.

**C2. Recording a payment overwrites the member**
The payment request body carries a full `member` object, and the use case saves that object as the member. A client can rename, reactivate or blank a member while recording a gift.
`RecordPaymentUseCase.java`
> **Status (3 Oct 2026):** fixed: `POST /api/payments` takes a `RecordPaymentRequest`; the use case loads the member by id.

**C3. Reminders never run, and would spam if they did**
There is no `@EnableScheduling`, so both jobs are dead. If enabled as written, the missed-months counter goes up every day (about 30 per month) and reminders go out every morning.
`PaymentReminderScheduler.java` · `Application.java`

> **Status (3 Oct 2026):** fixed: `@EnableScheduling` (SchedulingConfig), monthly crons, idempotent counter (`member.last_missed_count_month`, changeset 005), active members only. Reminder text personalisation is C4.

**C4. Reminder emails say "Dear {{member_name}}"**
The template placeholder is never filled in before sending.
`SendPaymentRemindersUseCase.java` · `EmailService.java`

> **Status (3 Oct 2026):** fixed: `MessageTemplates.personalize` applied at all three send sites.

**C5. Sessions end after 30 minutes, and refresh tokens work as access tokens**
The refresh cookie's path is `/v1/auth`, but the endpoint is `/api/auth/refresh`, so the browser never sends the cookie. Tokens carry no type claim, so a 30-day refresh token is accepted as a bearer access token.
`CookieUtils.java` · `JwtUtils.java`
> **Status (3 Oct 2026):** fixed: `typ` claim enforced by the filter and the refresh endpoint; refresh cookie path is `/api/auth`; covered by `AuthFlowIntegrationTest`; sessions renew: unauthenticated requests answer 401, which triggers the client refresh (audit C5 follow-up).

### Serious

**C6. Login tells an attacker which emails exist**
The raw domain message ("User with email '…' not found" vs "Invalid password provided") is returned to the client.
`AuthController.java`
> **Status (3 Oct 2026):** fixed: one message for unknown email and wrong password, equalised timing, only domain exceptions are surfaced.

**C7. Deleting a payment reports success and deletes nothing**
The handler checks that the payment exists and returns 200 without deleting it.
`PaymentController.java`
> **Status (3 Oct 2026):** resolved by removing the endpoint (payments are financial records). A void-with-audit-trail feature is future work.

**C8. Member edit accepts system-managed fields**
PUT binds the whole domain object, so a client can set `active`, `consecutiveMonthsMissed` and `lastPaymentDate` directly.
`MemberController.java`
> **Status (3 Oct 2026):** fixed: `MemberRequest` DTO; PUT loads the stored member and never takes counters or last payment date from the client.

**C9. Deleting a member erases financial records**
Hard delete with `ON DELETE CASCADE` removes all payments and delivery history. Giving records usually have a legal retention period.
`MemberController.java` · `001.schema-creation.sql`
> **Status (4 Oct 2026):** fixed: `DELETE /api/members/{id}` archives (status ARCHIVED, everything kept); migration `011` makes the payment and delivery foreign keys `ON DELETE RESTRICT` so the database refuses to erase history; `DELETE /api/members/{id}/permanent` (ADMIN) deletes only a member with no payments and no deliveries.

**C10. The data model cannot hold a family**
A unique, required email per member blocks children and shared inboxes, and there is no household table. Every people feature on the roadmap depends on changing this.
`Member.java` · `idx_member_email`

> **Status (5 Oct 2026):** fixed: email is optional and not unique (migration `009`), households and people without a membership exist (migrations `012` and `013`), and the legacy member columns are dropped (migration `014`); see [person-membership-plan.md](archive/person-membership-plan.md).

## 4. Other gaps

### Workflow gaps
- No path from first-time guest to follow-up to member
- No Sunday offering batch: one payment form per person
- No way for staff to set up a new staff user
- Communication audience is "all" or "overdue", nothing in between
- No member status lifecycle: transferred, deceased and inactive are all one boolean

### Usability
- ~~Payments are only accepted for the last 3 months~~ fixed: any month up to 10 years back, with an optional paid-on date
- The frontend has no role-aware navigation, so users click into 403 screens
- ~~The dashboard swallows errors and shows zeros, which hides outages~~ fixed: a failure is a 500 problem
- ~~English only: i18n is wired but has one locale~~ fixed: the UI ships Amharic and English, Amharic by default with English as the fallback, and the choice is saved on the account (`PUT /api/users/me/language`). The Amharic catalogue is machine-drafted and needs a native speaker's review; the backend's own strings (validation errors, emails, activity descriptions) are not translated. See [features/i18n.md](features/i18n.md)
- "Revenue" on the dashboard reads like a business, not a church

### Scalability limits
- `findAll()` sits behind every list, export, dashboard and the daily counter job
- No pagination or server-side filtering
- An unbounded cached thread pool per send, and no durable queue
- A single church is hard-coded in properties, with no multi-campus or tenant field
- CORS origins are hard-coded to localhost

### Compliance risks
- Church membership reveals religious belief, which is a special category under GDPR Art. 9 (assumption: EU operation)
- No consent or unsubscribe for bulk email
- No audit trail of who viewed or exported member data
- No subject-access export, and no rule for erasure versus retention
- No donation receipts or annual statements for tax-deductible giving

## 5. Quick wins

Each takes under a day. Do them in this order.

| Fix | Effort | Unblocks |
|-----|-------:|----------|
| Add `RoleHierarchy` (Admin > Staff > Volunteer > Member), replace the 16 `hasRole('USER')`, add a Liquibase changeset fixing the seed data and column default | 1 h | C1: Admin and Staff can work again *(done: 3cf5d84)* |
| Load the member by id inside `RecordPaymentUseCase` and accept only `memberId` | 30 min | C2 |
| Fix the refresh cookie path to `/api/auth`, add a `typ` claim and check it in the filter and the refresh endpoint | 1 h | C5 |
| Return one generic "Invalid email or password" message | 10 min | C6 |
| Enable scheduling, run the counter on the 1st of the month, send reminders monthly, fill in the member name | 1.5 h | C3, C4 |
| Add a member request DTO, archive members instead of deleting them, make payment delete real or remove it *(payment delete removed)* | 3 h | C7, C8, C9 |
| Restrict member export to Staff, and set `requiresRole` on frontend routes *(exports restricted to Staff)* | 30 min | Over-permitted Volunteer role |

Total: about 1.5 working days, including a MockMvc test per role per endpoint group so C1 cannot come back.

## 6. Roadmap

### Phase 0: Stabilise (1 week)
Goal: every role can do its job safely.
- All quick wins above
- A test of every role against every endpoint, running in CI
- Turn CSRF protection back on (or use SameSite=Strict), load secrets from the environment with no defaults, set up HTTPS
- Write to the existing `activity_log` on create, edit, export and sign-in *(done, October 2026; retention still open)*

### Phase 1: People foundation (3 to 4 weeks)
Goal: one accurate record per person and family.
- Split Person from Membership, make email optional, add households with relationships
- Status lifecycle: visitor, regular attender, member, inactive, transferred, deceased
- Address, birth date, photo, sacraments, custom fields, confidential pastoral notes
- Server-side search, filters and pagination
- CSV import with duplicate detection
- Admin user management with invites and password reset

### Phase 2: Engagement (4 to 6 weeks)
Goal: know who comes, who belongs, and who needs a call.
- Groups and ministries with leaders and scoped permissions
- Events and calendar, service attendance and group check-in
- Visitor follow-up: guest card, assigned caller, due date, outcome
- Targeted messages by group, status or household; templates with merge fields; unsubscribe and consent
- SMS through a provider, and a durable send queue

### Phase 3: Giving (3 to 4 weeks)
Goal: finances a treasurer can sign off on.
- `BigDecimal` money, funds and designations, pledges and campaigns
- Sunday batch entry and deposit reconciliation
- Receipts and annual giving statements
- A Treasurer role separate from Staff
- Online giving through a hosted processor, which keeps card data out of scope

### Phase 4: Self-service and scale (4 to 6 weeks)
Goal: members help keep their own data up to date.
- Member portal: own profile, household, giving history, opt-in directory
- MFA for Admin and Treasurer
- Report builder, scheduled reports, birthday and anniversary alerts
- Subject-access export, retention jobs, backups
- Multi-campus field, configurable CORS and church settings

## 7. Facts and assumptions

### Facts (from source)
- All ten defects in section 3, with the file references given
- The schema has 6 tables: `member`, `payment`, `communication`, `message_delivery`, `activity_log` (unused at audit time; written since October 2026), `users`
- At audit time there was one test class, `ApplicationTests`, and it is disabled
- Uncommitted changes at audit time touched only `App.vue`, `api.js` and `GlobalExceptionHandler.java`, none of which change authorization

### Assumptions
- EU jurisdiction (GDPR). If the church is in the US, use IRS contribution-statement rules instead.
- A single congregation of under 1,000 people, which is what the effort estimates assume
- If one developer works on it part-time, multiply the estimates by about 2
- C1 and C5 have not been confirmed by running the app
