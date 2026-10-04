# DashboardController

`src/main/java/io/github/membertracker/infrastructure/DashboardController.java`

Read-only summary figures (counts, revenue, recent payments, overdue members, activity feed) for the home screen; VOLUNTEER and above.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/dashboard/stats` | VOLUNTEER+ | none | `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}` |
| GET | `/api/dashboard/recent-payments` | VOLUNTEER+ | none | `Payment[]`, max 10 |
| GET | `/api/dashboard/overdue-members` | VOLUNTEER+ | none | `Member[]`, MEMBER-status members only, longest behind first |
| GET | `/api/dashboard/recent-activities` | VOLUNTEER+ | none | `{id, date, type, description}[]`, max 10 |

Roles and hierarchy: [../authentication.md](../authentication.md). The JSON keys and shapes are unchanged by the move to queries.

## Stats
Use case `GetDashboardStatsUseCase` (returns a `DashboardStats` record: four queries, no table is loaded).

| Field | Meaning | Source |
|-------|---------|--------|
| `totalMembers` | every member except the archived (any other status counts) | `MemberRepository.countNotArchived()` |
| `activeMembers` | members with status MEMBER (dues-paying) | `countDuesPaying()` |
| `overdueMembers` | MEMBER-status members with `consecutiveMonthsMissed >= 1` | `countDuesPayingWithMissedAtLeast(1)` |
| `monthlyRevenue` | sum of `amount` where the billing `period` is the current month; always a double (`0.0` when there are no payments) | `PaymentRepository.sumAmountByPeriod(YearMonth.now())`, a SQL `SUM` |

- Revenue is by billing `period`, not `paymentDate`: a payment made today for last month is excluded; an advance payment for this month made earlier is included.
- Overdue is a stored counter, not computed here; it is raised once per member per month by the monthly `UpdateMissingPaymentCountersUseCase` (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java`). A member who is behind but not dues-paying (inactive, deceased, transferred) or archived is not counted, so the stat tiles and the overdue list agree.

## Recent payments
- `GetRecentPaymentsUseCase.invoke(10)` -> `PaymentRepository.findRecent(10)`: newest `paymentDate` first, then newest id, limited in SQL.
- Returns the domain `Payment` as-is: nested `member` object, no `memberId` field (`src/main/java/io/github/membertracker/domain/model/Payment.java`). The derived fields `onTime`, `daysLate`, `forCurrentPeriod` and `valid` are no longer in the JSON (their getters were removed in `chore: remove unused domain methods`; the frontend never read them).

## Overdue members
- `GetMembersWithMissedPaymentsUseCase.invoke(1)` -> `MemberRepository.findDuesPayingWithMissedAtLeastOrderByMissedDesc(1)`: MEMBER-status members only, the one furthest behind first (then by name). The same use case serves `GET /api/members/overdue/{months}` and the send-to-overdue endpoint, so they are dues-paying-only too.

## Recent activities
- Derived, not read from the `activity_log` table (no Java code references that table; it is only in the SQL scripts, `src/main/resources/db/sql/001.schema-creation.sql:71`).
- Sources: `findRecent(5)` payments (newest `paymentDate`) + `CommunicationRepository.findRecent(5)` (newest `createdDate`, deliveries are not loaded).
- Payment item: `id=payment_<id>`, `type=payment`, `description="Payment received: $<amount %.2f>"`.
- Communication item: `id=comm_<id>`, `type=communication`, `description="Communication sent: <title>"`, `date` = `createdDate` truncated to date.
- Merged, sorted by date desc (stable: payments come before communications on the same date), first 10.
- Only `payment` and `communication` types are produced; no member events.

## Collaborators
- `GetDashboardStatsUseCase` -> `countNotArchived`, `countDuesPaying`, `countDuesPayingWithMissedAtLeast`, `sumAmountByPeriod`
- `GetMembersWithMissedPaymentsUseCase` -> `findDuesPayingWithMissedAtLeastOrderByMissedDesc`
- `GetRecentPaymentsUseCase` -> `PaymentRepository.findRecent`
- `GetRecentCommunicationsUseCase` -> `CommunicationRepository.findRecent`
- Models: `Member`, `Payment`, `Communication` (domain), returned unmapped.

## Data loaded per request
| Endpoint | Queries |
|----------|---------|
| stats | 3 counts + 1 `SUM` |
| recent-payments | 1 query, `LIMIT 10` |
| overdue-members | 1 query, MEMBER-status members only |
| recent-activities | 2 queries, `LIMIT 5` each |

- No table is loaded and sorted in Java any more. Indexes are not justified at under about 1,000 members (see [../todo.md](../todo.md)).

## Errors
- 401/403: from Spring Security / `@PreAuthorize` only (see [../architecture.md](../architecture.md) error format).
- No endpoint catches exceptions: a failure reaches `GlobalExceptionHandler` and is a 500 `application/problem+json` with the generic detail (the frontend shows an inline alert). An outage is never shown as zeros or empty lists. Covered by `DashboardErrorsTest`; the numbers by `DashboardQueriesIntegrationTest`.

## Gotchas
- Activity description hardcodes `$`.
- `recent-payments` and the activity feed read payments with their member (an eager many-to-one): at most 10 extra member loads per request.
