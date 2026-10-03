# Architecture

*Last checked against the code: 3 October 2026.*

How the code is organised, where new logic belongs, and the design decisions made so far.

## Stack

- **Backend:** Java 17, Spring Boot 3.4.5, Spring Security, Spring Data JPA, Liquibase, MySQL, Thymeleaf (email templates only)
- **Frontend:** Vue 3, Vite 5, Vue Router 4, Pinia, Axios, Bootstrap 5, date-fns, vue-i18n
- **Build:** Gradle. `bootJar` packages the built frontend into the backend JAR.

## Backend layers

Clean architecture: dependencies point inwards, and the domain layer depends on no framework.

```
src/main/java/io/github/membertracker/
├── domain/
│   ├── model/          Member, Payment, User, Communication, MessageDelivery
│   ├── valueobject/    Email, PhoneNumber
│   ├── enumeration/    PaymentMethod, UserRole, CommunicationType
│   ├── exception/      DomainException + Member/Payment/User subclasses
│   ├── policy/         MembershipPolicy, DefaultMembershipPolicy
│   └── repository/     Repository interfaces
├── usecase/            One class per operation (28), e.g. RecordPaymentUseCase
├── infrastructure/
│   ├── *Controller     REST controllers
│   ├── config/         SecurityConfig, WebMvcConfig, properties classes
│   ├── dto/            Request and response DTOs
│   ├── filter/         JwtAuthenticationFilter
│   ├── handler/        GlobalExceptionHandler
│   ├── persistence/    JPA entities and repository implementations
│   ├── security/
│   └── service/        EmailService
├── scheduler/          PaymentReminderScheduler (monthly, enabled by SchedulingConfig)
└── utils/              CookieUtils, JwtUtils
```

Database migrations: `src/main/resources/db/master.xml` and `db/sql/NNN.*.sql`.

## Where logic belongs

The project uses a hybrid of rich domain models and policies.

| Put it in… | When the logic… | Example |
|------------|-----------------|---------|
| **Domain model** | Uses only the entity's own data: invariants, state changes, simple calculations | `Member.recordPayment()`, `Member.deactivate()`, `Payment.validatePeriod()`, `User.recordFailedLoginAttempt()` |
| **Policy** | Is a business rule that changes often, involves several entities, or needs configuration | `MembershipPolicy.shouldDeactivate()`, `shouldSendReminder()`, `canReactivate()` |
| **Use case** | Coordinates several steps: transactions, repositories, external services | `RecordPaymentUseCase` |

Shape of a use case that uses all three (`RecordPaymentUseCase` loads the member by id, then applies the domain behaviour):

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

It does not call `MembershipPolicy` today.

`Communication` owns its sent state (`markAsSent()`, `isSent()`) and its deliveries (`addDelivery()`).

### Testing by layer

- **Domain models and policies:** plain unit tests, no Spring.
- **Use cases:** integration tests covering the whole workflow.
- **Controllers:** MockMvc tests for every role against every endpoint group (`RoleAuthorizationTest`, 125 cases).

Current coverage: `RoleAuthorizationTest` and `ApplicationTests`. `ApplicationTests` is disabled.

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
├── stores/       Pinia stores: auth, app, member, payment, communication
├── services/     api.js — the only Axios instance
├── router/       Routes and auth guards
├── utils/        Generic helpers (formatDate, debounce, …)
├── constants/    Shared constants
├── plugin/       AxiosPlugin
└── i18n.js       Translations (one locale so far)
```

- Shared state goes in `stores/`. There is no `composables/` folder: it was removed in a dead-code cleanup.
- All HTTP calls go through `services/api.js`.
- Notifications use `appStore.addNotification({ message, type, duration })`. `showToast()` in `utils/index.js` is deprecated.

## Decisions

| Date | Decision | Why | Status |
|------|----------|-----|--------|
| 2025-09 | Stateless JWT in HttpOnly cookies | Tokens stay out of JavaScript; no session store | In use. Details in [authentication.md](authentication.md) |
| 2026-02 | Hybrid domain model (models + policies + use cases) | Avoids an anemic model without putting volatile rules in entities | In use |
| 2026-02 | Money as `Double`, not `BigDecimal` | Simpler arithmetic and JSON | **Under review:** the [functionality audit](functionality-audit.md) recommends going back to `BigDecimal` before adding funds and receipts |
| 2026-02 | Cached thread pool for email sending, not virtual threads | Keeps the project on Java 17 | In use. The audit flags it as unbounded and not durable |
| 2026-02 | Self-registration disabled | Only church staff should have accounts | In use. There is no admin user management yet |
| 2026-10 | HTTPS via Caddy reverse proxy, not Spring SSL | Automatic certificate renewal; app config stays simple | In use. See [development.md](development.md#https-caddy-reverse-proxy) |
| 2026-10 | "Send to all" means active members only | Inactive includes lapsed, transferred and deceased members | In use |
| 2026-10 | Any payment amount above 0 is valid; no fixed minimum | Dues vary by family and gifts can be small | In use |
| 2026-10 | Payment reminders start 7 days before the due date (inclusive) | Matches REMINDER_DAYS_BEFORE_DUE | In use |
| 2026-10 | OpenAPI/Swagger UI only under the dev profile | Public endpoint list helps attackers; devs still get docs | In use |
| 2026-10 | All API errors are RFC 7807 ProblemDetail; no rejected values echoed | One format for the frontend; no input reflected back | In use |
| 2026-10 | Member search, sort and filters stay in the browser | Under ~1,000 members the full list is ~200 kB; a paged API adds complexity for no visible gain | In use. Revisit above ~2,000 members |
| 2026-10 | Unauthenticated requests answer 401 (problem+json); 403 only for authenticated users lacking the role | The client refreshes the session on 401 | In use |
| 2026-10 | The overdue counter is raised once per member per month by a monthly job (idempotent through member.last_missed_count_month) | A daily job would over-count; re-runs and restarts must be safe | In use |
| 2026-10 | Members are written through MemberRequest; counters and payment dates are system-managed and never client-settable | Stops mass assignment; keeps the monthly job's marker intact | In use |
| 2026-10 | The default profile has no secret defaults; local values live only in the dev profile | A production JAR must never sign tokens with a public key or connect as root/password | In use |
| 2026-10 | CSRF protection is on: token cookie + X-XSRF-TOKEN header, no exempt endpoints | Auth uses cookies, which browsers send automatically | In use |
| 2026-10 | YearMonth is stored as YYYY-MM text through an attribute converter | The column is VARCHAR(7); without a converter Hibernate serialised the value as binary and failed on MySQL | In use |
| 2026-10 | CSV exports are built in memory and returned as a plain response, UTF-8 with BOM | Streaming gained nothing at this size and hung behind the dev proxy; the BOM makes Excel read Amharic names | In use |
| 2026-10 | Roles ADMIN > STAFF > VOLUNTEER > MEMBER with `RoleHierarchy` | Replaces ADMIN/MANAGER/USER and the planned TREASURER/VIEWER | Done (3cf5d84; frontend routes in this change) |
