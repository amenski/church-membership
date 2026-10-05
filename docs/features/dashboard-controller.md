# DashboardController

`src/main/java/io/github/membertracker/infrastructure/DashboardController.java`

Read-only summary figures (counts, revenue and the month-by-month collected chart) for the home screen; VOLUNTEER and above.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/dashboard/stats` | VOLUNTEER+ | none | `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}` |
| GET | `/api/dashboard/collected-by-month` | VOLUNTEER+ | none | `{month, amount}[]`, 12 rows, oldest first; `month` is `yyyy-MM` |

Roles and hierarchy: [../authentication.md](../authentication.md). The JSON keys and shapes are unchanged by the move to queries.

The Overview's other lists do not come from here: who owes dues is built on the screen from `GET /api/members` and `GET /api/payments/paid-months`, and the latest payments from one page of `GET /api/payments/page` ([dashboard.md](dashboard.md)).

## Stats
Use case `GetDashboardStatsUseCase` (returns a `DashboardStats` record: four queries, no table is loaded).

| Field | Meaning | Source |
|-------|---------|--------|
| `totalMembers` | every member except the archived (any other status counts) | `MemberRepository.countNotArchived()` |
| `activeMembers` | members with status MEMBER (dues-paying) | `countDuesPaying()` |
| `overdueMembers` | MEMBER-status members with `consecutiveMonthsMissed >= 1` | `countDuesPayingWithMissedAtLeast(1)` |
| `monthlyRevenue` | sum of `amount` where the billing `period` is the current month; always a double (`0.0` when there are no payments) | `PaymentRepository.sumAmountByPeriod(YearMonth.now())`, a SQL `SUM` |

- Revenue is by billing `period`, not `paymentDate`: a payment made today for last month is excluded; an advance payment for this month made earlier is included.
- Overdue is a stored counter, not computed here; it is raised once per member per month by the monthly `UpdateMissingPaymentCountersUseCase` (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java`). A member who is behind but not dues-paying (inactive, deceased, transferred) or archived is not counted, so the stat tiles and the dues ledger agree.

## Collected by month
- `GetCollectedByMonthUseCase.invoke(12)` lists the last 12 billing months ending with the current one, oldest first. Each row is `PaymentRepository.sumAmountByPeriod(month)`, the same rule as `monthlyRevenue`: the sum follows the payment's `period`, not the day it was recorded. A month with no payments is returned as 0, so there are no gaps.

## Collaborators
- `GetDashboardStatsUseCase` -> `countNotArchived`, `countDuesPaying`, `countDuesPayingWithMissedAtLeast`, `sumAmountByPeriod`
- `GetCollectedByMonthUseCase` -> `sumAmountByPeriod`

## Data loaded per request
| Endpoint | Queries |
|----------|---------|
| stats | 3 counts + 1 `SUM` |
| collected-by-month | 12 `SUM` queries, one per month |

- No table is loaded and sorted in Java any more. Indexes are not justified at under about 1,000 members (see [../todo.md](../todo.md)).

## Errors
- 401/403: from Spring Security / `@PreAuthorize` only (see [../architecture.md](../architecture.md) error format).
- No endpoint catches exceptions: a failure reaches `GlobalExceptionHandler` and is a 500 `application/problem+json` with the generic detail (the frontend shows an inline alert). An outage is never shown as zeros or empty lists. Covered by `DashboardErrorsTest`; the numbers by `DashboardQueriesIntegrationTest`.
