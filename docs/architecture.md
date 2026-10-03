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
│   ├── enumeration/    PaymentMethod, UserRole
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
├── scheduler/          PaymentReminderScheduler (not running: no @EnableScheduling)
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

The intended shape of a use case that uses all three. Today's `RecordPaymentUseCase` saves the member object sent by the client instead of loading it by id (C2 in the audit).

```java
@Transactional
public Payment invoke(Payment payment) {
    Member member = memberRepository.findById(payment.getMemberId()).orElseThrow();
    member.recordPayment(payment);                                    // domain behaviour
    if (membershipPolicy.shouldDeactivate(member, LocalDate.now())) {  // policy decision
        member.deactivate();
    }
    memberRepository.save(member);
    return paymentRepository.save(payment);
}
```

**Not done yet:** `Communication` has no behaviour (getters and setters only), and `CommunicationType` is still an inner enum of `Communication`.

### Testing by layer

- **Domain models and policies:** plain unit tests, no Spring.
- **Use cases:** integration tests covering the whole workflow.
- **Controllers:** MockMvc tests for every role against every endpoint group (`RoleAuthorizationTest`, uncommitted).

Current coverage: `RoleAuthorizationTest` and `ApplicationTests`. `ApplicationTests` is disabled.

## Validation and errors

- Bean Validation annotations on domain models and request DTOs (for example `@DecimalMin(0.01)` on payment amounts).
- Controllers use `@Validated`, `@Valid` on request bodies, and `@Positive` or `@Min(1)` on path variables.
- `infrastructure/handler/GlobalExceptionHandler` turns validation and domain exceptions into structured JSON errors.
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
| 2026-10 | Roles ADMIN > STAFF > VOLUNTEER > MEMBER with `RoleHierarchy` | Replaces ADMIN/MANAGER/USER and the planned TREASURER/VIEWER | In the working tree, not committed |
