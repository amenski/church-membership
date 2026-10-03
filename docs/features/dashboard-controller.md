# DashboardController

`src/main/java/io/github/membertracker/infrastructure/DashboardController.java`

Read-only summary figures (counts, revenue, recent payments, overdue members, activity feed) for the home screen; VOLUNTEER and above.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/dashboard/stats` | VOLUNTEER+ (`:48`) | none | `{totalMembers, activeMembers, overdueMembers, monthlyRevenue}` |
| GET | `/api/dashboard/recent-payments` | VOLUNTEER+ (`:103`) | none | `Payment[]`, max 10 |
| GET | `/api/dashboard/overdue-members` | VOLUNTEER+ (`:128`) | none | `Member[]` |
| GET | `/api/dashboard/recent-activities` | VOLUNTEER+ (`:141`) | none | `{id, date, type, description}[]`, max 10 |

Roles and hierarchy: [../authentication.md](../authentication.md).

## Stats (`:50`)
| Field | Meaning | Source |
|-------|---------|--------|
| `totalMembers` | all members, active or not | `findAll()` size (`:55`) |
| `activeMembers` | members with `active = true` | `findByActive(true)` size (`:59`) |
| `overdueMembers` | members with `consecutiveMonthsMissed >= 1` | `invoke(1)` (`:63`) |
| `monthlyRevenue` | sum of `amount` where `payment.period == current YearMonth` | all payments filtered in memory (`:67-86`) |

- Revenue is by billing `period`, not `paymentDate`: a payment made today for last month is excluded; an advance payment for this month made earlier is included.
- Overdue is a stored counter, not computed here; it is incremented by `UpdateMissingPaymentCountersUseCase` (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java:31`).

## Recent payments
- Loads all payments, sorts by `paymentDate` desc, takes 10 (`:107-120`).
- Returns the domain `Payment` as-is: nested `member` object, no `memberId` field (`src/main/java/io/github/membertracker/domain/model/Payment.java:15-18`).

## Recent activities
- Derived, not read from the `activity_log` table (no Java code references that table; it is only in the SQL scripts, `src/main/resources/db/sql/001.schema-creation.sql:71`).
- Sources: 5 newest payments by `paymentDate` (`:148-172`) + 5 newest communications by `createdDate` (`:175-199`).
- Payment item: `id=payment_<id>`, `type=payment`, `description="Payment received: $<amount %.2f>"`.
- Communication item: `id=comm_<id>`, `type=communication`, `description="Communication sent: <title>"`, `date` = `createdDate` truncated to date.
- Merged, sorted by date desc, first 10 (`:202-208`). Same-date ordering between payments and communications is not defined by an explicit rule.
- Only `payment` and `communication` types are produced; no member events.

## Collaborators
- `GetAllMembersUseCase` -> `memberRepository.findAll()` (`src/main/java/io/github/membertracker/usecase/GetAllMembersUseCase.java:17`)
- `GetActiveMembersUseCase` -> `findByActive(true)` (`src/main/java/io/github/membertracker/usecase/GetActiveMembersUseCase.java:22`)
- `GetMembersWithMissedPaymentsUseCase` -> `findByConsecutiveMonthsMissedGreaterThanEqual` (`src/main/java/io/github/membertracker/usecase/GetMembersWithMissedPaymentsUseCase.java:23`)
- `GetAllPaymentsUseCase` -> `paymentRepository.findAll()` (`src/main/java/io/github/membertracker/usecase/GetAllPaymentsUseCase.java:22`)
- `GetAllCommunicationsUseCase` -> `communicationRepository.findAll()` (`src/main/java/io/github/membertracker/usecase/GetAllCommunicationsUseCase.java:17`)
- Models: `Member`, `Payment`, `Communication` (domain), returned unmapped.

## Data loaded per request
| Endpoint | Full-table loads |
|----------|------------------|
| stats | members x2 (all + active), overdue members, all payments |
| recent-payments | all payments |
| overdue-members | overdue members |
| recent-activities | all payments + all communications (incl. deliveries) |

- Sorting, filtering and limits are done in Java after load; no pagination or DB-side limits.
- The dashboard view fires all four plus `GET /api/members` in parallel (see [dashboard-view.md](dashboard-view.md)): payments are loaded 3 times per page load.

## Errors
- 401/403: from Spring Security / `@PreAuthorize` only (see [../architecture.md](../architecture.md) error format).
- Every endpoint catches `Exception` and returns 200: stats with zeros (`:90-98`), others with `[]` (`:122-124`, `:135-137`, `:209-211`). Failures never reach the client as 5xx.

## Gotchas
- Swallowed errors: a DB failure shows as "0 members / $0 / empty lists" with status 200; only `/stats` logs it (`:92`).
- Per-item try/catch around casts silently drops or mis-sorts items (`:75`, `:115`, `:169`).
- Stats failure fallback sets `monthlyRevenue` to int `0`, success path a `Double` (`:97` vs `:87`); JSON renders `0` vs e.g. `120.0`.
- Activity description hardcodes `$` (`:167`).
