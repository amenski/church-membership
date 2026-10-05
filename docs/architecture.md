# Architecture

*Last checked against the code: 5 October 2026.*

How the code is organised, where new logic belongs, and the design decisions made so far.

## Stack

- **Backend:** Java 17, Spring Boot 3.4.5, Spring Security, Spring Data JPA, Liquibase, MySQL
- **Frontend:** Vue 3, Vite 5, Vue Router 4, Pinia, Axios, Tailwind CSS v4 (no Bootstrap), date-fns, vue-i18n
- **Build:** Gradle. `bootJar` packages the built frontend into the backend JAR.

## Backend layers

Clean architecture: dependencies point inwards, and the domain layer depends on no framework.

```
src/main/java/io/github/membertracker/
├── domain/
│   ├── model/          Member, Person, Household, Payment, User, Communication, MessageDelivery, ActivityLogEntry, and read models such as PaymentSummary and PageResult
│   ├── valueobject/    Email, MonthLabel
│   ├── enumeration/    PaymentMethod, PaymentSortField, UserRole, CommunicationType, ActivityType, MemberStatus
│   ├── exception/      DomainException + Communication/Household/Member/Payment/Person/User subclasses
│   ├── repository/     Repository interfaces (incl. ActivityLogRepository)
│   └── service/        Ports: CurrentActor (who is acting, implemented in infrastructure/security)
├── usecase/            One class per operation, e.g. RecordPaymentUseCase; RecordActivityUseCase writes the audit trail
├── infrastructure/
│   ├── *Controller     REST controllers (incl. ActivityLogController)
│   ├── config/         SecurityConfig, WebMvcConfig, properties classes
│   ├── dto/            Request and response DTOs
│   ├── filter/         JwtAuthenticationFilter, CsrfCookieFilter
│   ├── handler/        GlobalExceptionHandler
│   ├── persistence/    JPA entities, repository implementations, and mapper/ (MemberPersistenceMapper: the one place a member entity and a Member are converted)
│   ├── security/       LoginAttemptLimiter, SecurityContextCurrentActor, ArchivedVisibility
│   └── service/        EmailService
├── scheduler/          PaymentReminderScheduler (monthly, enabled by SchedulingConfig)
└── utils/              CookieUtils, JwtUtils, CsvUtils, MessageTemplates
```

Database migrations: `src/main/resources/db/master.xml` (it includes the whole `db/sql` folder) and `db/sql/NNN.*.sql`, 001 to 014. Migration 012 creates the `person` and `household` tables (step 7 of the [person plan](archive/person-membership-plan.md)); 013 backfills one `person` per member (same id) and adds `member.person_id` (NOT NULL, unique, FK RESTRICT); 014 drops the legacy `name`, `email`, `phone` and `active` columns of `member`.

## Where logic belongs

The project uses rich domain models, with use cases coordinating them. A policy layer existed once and was removed (see Decisions).

| Put it in… | When the logic… | Example |
|------------|-----------------|---------|
| **Domain model** | Uses only the entity's own data: invariants, state changes, simple calculations | `Member.recordPayment()`, `Member.deactivate()`, `Payment.validatePeriod()`, `User.recordFailedLoginAttempt()` |
| **Use case** | Coordinates several steps: transactions, repositories, external services | `RecordPaymentUseCase` |

Shape of a use case that uses both (`RecordPaymentUseCase` loads the member by id, then applies the domain behaviour):

```java
public Payment invoke(Long memberId, Double amount, PaymentMethod paymentMethod,
                      YearMonth period, String notes) {
    Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));

    Payment payment = new Payment(member, period != null ? period : YearMonth.now(), amount, paymentMethod);
    payment.setNotes(notes);
    payment.validateAmount();                                          // domain rules
    payment.validatePeriod();

    if (paymentRepository.existsByMemberAndPeriod(member, payment.getPeriod())) {
        throw MemberDomainException.duplicatePaymentForPeriod(member.getName(), payment.getPeriod().toString());
    }

    payment.markAsProcessed();
    member.recordPayment(payment);                                     // domain behaviour
    memberRepository.save(member);
    return paymentRepository.save(payment);
}
```

`Communication` owns its sent state (`markAsSent()`, `isSent()`) and its deliveries (`addDelivery()`).

### Testing by layer

- **Domain models:** plain unit tests, no Spring.
- **Use cases:** integration tests covering the whole workflow.
- **Controllers:** MockMvc tests for every role against every endpoint group (`RoleAuthorizationTest`, every role against every endpoint group).

Backend: 748 tests, 1 skipped (`ApplicationTests`, disabled), and frontend: 326 tests, as of the last full run on 5 October 2026 (`./gradlew test`, `npm test` in `frontend/`). `RoleAuthorizationTest` covers every role against every endpoint group.

## Validation and errors

- Bean Validation annotations on domain models and request DTOs (for example `@DecimalMin(0.01)` on payment amounts).
- Controllers use `@Validated`, `@Valid` on request bodies, and `@Positive` or `@Min(1)` on path variables.
- All errors are RFC 7807 `ProblemDetail` (`detail`, plus `path`, `timestamp`, optional `code` and `errors[{field,message}]`), built by `infrastructure/handler/GlobalExceptionHandler` and `ProblemDetails`.
- Rejected values are never echoed back.
- Unexpected errors return a generic 500 and are logged server-side.
- Domain code throws `DomainException` subclasses, not generic exceptions.

## Frontend conventions

```
frontend/src/
├── views/        One component per page
├── components/   Shared components (Pager, MemberPicker, YearStrip, dialogs, …)
├── stores/       Pinia stores: auth, app (notifications)
├── services/     api.js — the only Axios instance
├── router/       Routes and auth guards
├── ui/           classes.js — the class strings the views share
├── utils/        Helpers (formatDate, formatMoney, paging, member filters, …)
└── i18n.js       Translations (one locale so far)
```

- Shared state goes in `stores/`. There is no `composables/` folder: it was removed in a dead-code cleanup.
- All HTTP calls go through `services/api.js`.
- Styling is Tailwind utilities written in the templates (no class prefix, no `<style>` blocks); tokens and base rules are in `assets/styles/tailwind.css`, shared class strings in `ui/classes.js`, shared patterns are components. See [design.md](design.md).
- Notifications use `appStore.addNotification({ message, type, duration })`.

## Decisions

| Date | Decision | Why | Status |
|------|----------|-----|--------|
| 2026-10 | The frontend is a dense admin dashboard: IBM Plex Sans, one accent, inline-SVG icons, no icon or serif font, no landing page, and the Overview leads with stat tiles | The product is an internal tool used weekly; decoration cost bytes and reading time | In use. See [design.md](design.md) |
| 2025-09 | Stateless JWT in HttpOnly cookies | Tokens stay out of JavaScript; no session store | In use. Details in [authentication.md](authentication.md) |
| 2026-02 | Hybrid domain model (models + policies + use cases) | Avoids an anemic model without putting volatile rules in entities | Removed: the policy was never wired in; see git history (`chore: remove unused use cases, the membership policy and PhoneNumber`) |
| 2026-02 | Money as `Double`, not `BigDecimal` | Simpler arithmetic and JSON | **Under review:** the [functionality audit](functionality-audit.md) recommends going back to `BigDecimal` before adding funds and receipts |
| 2026-02 | Cached thread pool for email sending, not virtual threads | Keeps the project on Java 17 | In use. The audit flags it as unbounded and not durable |
| 2026-02 | Self-registration disabled | Only church staff should have accounts | In use. There is no admin user management yet |
| 2026-10 | HTTPS via Caddy reverse proxy, not Spring SSL | Automatic certificate renewal; app config stays simple | In use. See [development.md](development.md#https-caddy-reverse-proxy) |
| 2026-10 | "Send to all" means members with status MEMBER only (`Recipients.reachable` also drops every other status) | Inactive, transferred and deceased people must not be billed or written to | In use |
| 2026-10 | Any payment amount above 0 is valid; no fixed minimum | Dues vary by family and gifts can be small | In use |
| 2026-10 | Reminders go only to members who are already behind; there is no pre-due window | The old pre-due policy code was removed, so nothing sends before a due date. Whether to bring a window back is open in [todo.md](todo.md) | In use |
| 2026-10 | OpenAPI/Swagger UI only under the dev profile | Public endpoint list helps attackers; devs still get docs | In use |
| 2026-10 | All API errors are RFC 7807 ProblemDetail; no rejected values echoed | One format for the frontend; no input reflected back | In use |
| 2026-10 | Member search, sort and filters stay in the browser | Under ~1,000 members the full list is ~200 kB; a paged API adds complexity for no visible gain | In use. Revisit above ~2,000 members (Members, Households and Messages page in the browser) |
| 2026-10 | Payments gain `GET /api/payments/page` (search, method, sort, paging) and `GET /api/payments/paid-months`; the plain `GET /api/payments` list is kept for exports and the screens that need every payment | Payments grow every month and are the largest list; screens that only draw a table or a year strip should not download all of them | In use: Payments pages on the server, and the year strips (Members, Overview, Messages, Households) and the member picker read `/paid-months`; no screen downloads every payment any more (only the CSV export reads them all). `GET /api/payments/summary` gives the Payments figures. Members, Households and Messages page in the browser |
| 2026-10 | Unauthenticated requests answer 401 (problem+json); 403 only for authenticated users lacking the role | The client refreshes the session on 401 | In use |
| 2026-10 | The overdue counter is raised once per member per month by a monthly job (idempotent through member.last_missed_count_month) | A daily job would over-count; re-runs and restarts must be safe | In use |
| 2026-10 | Members are written through MemberRequest; counters and payment dates are system-managed and never client-settable | Stops mass assignment; keeps the monthly job's marker intact | In use |
| 2026-10 | The default profile has no secret defaults; local values live only in the dev profile | A production JAR must never sign tokens with a public key or connect as root/password | In use |
| 2026-10 | CSRF protection is on: token cookie + X-XSRF-TOKEN header, no exempt endpoints | Auth uses cookies, which browsers send automatically | In use |
| 2026-10 | YearMonth is stored as YYYY-MM text through an attribute converter | The column is VARCHAR(7); without a converter Hibernate serialised the value as binary and failed on MySQL | In use |
| 2026-10 | CSV exports are built in memory and returned as a plain response, UTF-8 with BOM | Streaming gained nothing at this size and hung behind the dev proxy; the BOM makes Excel read Amharic names | In use |
| 2026-10 | Audit entries are written best-effort inside the use cases through a CurrentActor port | A failing audit write must not block the action; the domain stays free of Spring Security | In use |
| 2026-10 | Membership status (MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED) is stored on the member; the old `active` flag and column are gone (migration `014`, step 12) | Dues, reminders and messages need more than on/off; the migration must stay reversible | In use |
| 2026-10 | Members are read by status: `findDuesPaying`/`countDuesPaying` (status MEMBER) drive dues, reminders, messages and payments; every list and count except `GET /api/members?archived=true` (ADMIN) hides ARCHIVED | One rule table (`MemberStatus`) instead of `active` checks scattered around | In use |
| 2026-10 | Members are archived, not deleted; payments and deliveries use ON DELETE RESTRICT; a permanent delete is an admin API that refuses when history exists | A hard delete erased payment history (audit C9) | In use |
| 2026-10 | `person` and `household` tables exist (migration 012) but no code reads or writes them until step 8; `person.email` is indexed, not unique | Expand first: the schema ships and is proven on a copy before any code depends on it | Done: both tables are read and written since steps 8 and 9 (see the rows below) |
| 2026-10 | `member.name`, `email`, `phone` and `active` were dropped (migration `014.drop-legacy-member-columns.sql`, plan step 12, the contract step): name, email and phone live on `person` only, written by `MemberDbRepository.save` through the linked person (`MemberPersistenceMapper.writeToPerson`); the status replaces `active` in the JSON, the request and the CSV; the overdue list sorts by `person.name`; payment and delivery list queries join member and person | Expand, then contract: reads moved to `person` at step 9 and the drift query (since retired) stayed at 0 for a release before the columns went; one home per fact, no dual-write to keep in step | In use (each DROP has a rollback that restores the values from `person`) |
| 2026-10 | A household is real but minimal (name, address, notes; no head, no dues): `/api/households` (VOLUNTEER read, STAFF write, ADMIN delete), members join through `person.household_id` via an optional `householdId` on the member request (absent keeps, null clears); a household with any person cannot be deleted (409), archived members stay in it and only an ADMIN sees them | Decisions a and f of the person plan; people must never lose their household behind their back | In use (API and screens, step 10) |
| 2026-10 | People without a membership (dependents) are persons with no `member` row: `/api/people` (VOLUNTEER read, STAFF write and start a membership, ADMIN delete only when there is none); a household detail lists its `people` (every person) beside `members` (the memberships) and its list adds `personCount`; dues, reminders, messages, exports and dashboard counts stay on `member` rows | Decision f of the person plan: dues are per membership, and a dependent must never leak into a members-only number | In use (API and the people list on the household detail, step 11; the drift query was retired with step 12) |
| 2026-10 | A signed-in user is matched to a member by email for `GET /api/me/dues`: person email, trimmed and case-insensitive, status not ARCHIVED; no match or more than one match answers 404, never a guess | A shared family email must not expose another person's dues; an explicit user-to-member link is the follow-up if emails prove unreliable | In use |
| 2026-10 | Roles ADMIN > STAFF > VOLUNTEER > MEMBER with `RoleHierarchy` | Replaces ADMIN/MANAGER/USER and the planned TREASURER/VIEWER | Done (3cf5d84) |
| 2026-10 | Sample data is dev-only: `002.sample-data.sql` carries the Liquibase context `dev`, the default profile runs context `prod` and the `dev` profile runs `dev`; the first administrator comes from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` while the users table is empty (`BootstrapFirstAdminUseCase`, started by `BootstrapAdminRunner`) | A production database must not ship known logins or demo members, and with no registration it still needs a first administrator. The context is not part of the checksum, so databases that already ran 002 are unaffected. The default profile must name a context, because with none Liquibase runs every changeset | In use (checked on a fresh MySQL, on a copy of the demo database and under the dev profile) |
