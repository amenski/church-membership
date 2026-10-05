# AuthController

`src/main/java/io/github/membertracker/infrastructure/AuthController.java`

Public sign-in, refresh and sign-out endpoints that issue and clear the `sid` / `sid_refresh` JWT cookies. Cookie, CSRF and lockout details: [../authentication.md](../authentication.md).

## Endpoints
All under `/api/auth`. No `@PreAuthorize`; `/api/auth/**` is `permitAll` (`infrastructure/config/SecurityConfig.java`). CSRF still applies: every POST below needs the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie, otherwise 403 problem "Invalid or missing CSRF token" (the cookie is set on any earlier response, e.g. `GET /api/users/me`).

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| POST | `/login` (`AuthController.java`) | public | `LoginRequest {email, password}` | 200 `{user: {email, role}}` + 2 `Set-Cookie`; role is the first authority minus `ROLE_` |
| POST | `/register` | public | `RegisterRequest`, ignored | always 403 |
| POST | `/refresh` | public (needs `sid_refresh` cookie) | none | 204 + 2 new `Set-Cookie` |
| POST | `/logout` | public | none | 204 + 2 clearing `Set-Cookie` (Max-Age 0) |

## Actions
- `login` -> `AuthenticateUserUseCase.invoke(email, password)`, then signs access + refresh JWTs and builds both cookies
- `refresh` -> reads the refresh cookie named by `auth.cookies.refresh-name` (`getRefreshTokenFromCookie`), `JwtUtils.validateToken(.., "refresh")`, loads user by token subject, issues a fresh pair keeping the token's `auth_time` and expiry (rejected: no `auth_time`, or issued before the user's last password change). Rotation keeps the original expiry (`auth_time` + 30 days); the old refresh token is not revoked.
- `logout` -> only clears cookies; tokens stay valid until they expire.
- `register` -> returns 403 without touching any use case.

## Collaborators
- `usecase/AuthenticateUserUseCase.java`: lookup by email; for an unknown email a dummy BCrypt check (hash memoised in `dummyHash()`) then `invalidCredentials()`; clears an expired lock, rejects a locked account with the generic error (dummy BCrypt, password not checked), BCrypt match (a failure goes to `recordFailedLogin`), then checks enabled and credentials non-expired; `resetFailedLogins` on success. No full-user save.
- `usecase/LoadUserByUsernameUseCase.java`: used by `refresh` (`AuthController.java`).
- `utils/CookieUtils.java,28,40,47`: builds access, refresh and clearing cookies from `AuthProperties`.
- `utils/JwtUtils.java,62,98`: HS256 token generation and typed validation. Tokens carry subject, `iat`, `exp`, a `typ` claim (`access` / `refresh`) and `auth_time` (original sign-in, epoch seconds).
- `infrastructure/config/AuthProperties.java`: `auth.*` (TTLs, cookie names and flags, secret).
- `infrastructure/filter/JwtAuthenticationFilter.java`: accepts only `access` tokens from the `sid` cookie, then the Bearer header, on every request, including the public `/api/auth/**` paths. If the token's user cannot be loaded, the request continues unauthenticated.
- Models: `domain/model/User` (returned by the use case; only email + role leave the controller).

## Errors
RFC 7807 via `ProblemDetails.of` (format: [../architecture.md](../architecture.md)).

| Status | Source |
|--------|--------|
| 400 | `login`: `UserDomainException` from the use case, message passed through as `detail`; any other exception goes to `GlobalExceptionHandler` (500, generic message) |
| 400 | `refresh`: no cookie, invalid, expired or wrong-type token, any other failure |
| 429 | `login`: throttled by `LoginAttemptLimiter` (`Retry-After` seconds) |
| 403 | `register`: "Registration is disabled. Please contact administrator for access." |

Login failures are 400, not 401, and refresh failures are 400. Unknown email, wrong password and locked account return the same message, "Invalid email or password. After several failed attempts an account is locked for 15 minutes." (`UserDomainException.invalidCredentials`). `login` first asks `LoginAttemptLimiter.check(request.getRemoteAddr(), email)`: over 10 failures for the email or 30 for the IP in 10 minutes it answers 429 "Too many sign-in attempts. Try again in a few minutes." with `Retry-After`; a domain failure is recorded, a success clears the email.

## Side effects
- Login with a wrong password increments the failed-attempt counter with one SQL update; the fifth failure locks the account for 15 minutes (`users.locked_until`).
- Successful login resets the counter and clears any lock with one SQL update; the user row is never saved whole.
- The in-memory limiter records failures per IP and per email.
- A successful login adds a `SIGN_IN` entry to the activity log with the email as actor, passed explicitly because the security context is still empty (`AuthController.java`). Failed logins are not recorded: [activity.md](activity.md).
- No emails, no jobs.

## Gotchas
- Tokens issued before the `typ` claim existed are rejected by both the filter and `/refresh`, so everyone signed in again once after that deploy.
- A still-valid `sid` for a user who was deleted, disabled, locked or whose credentials expired is treated as signed out: the filter drops it (`JwtAuthenticationFilter.java`), protected paths answer 401, and `/login`, `/refresh` and `/logout` work normally. (Before, the load failure surfaced as a 500, even on `/login`, for up to 30 minutes.)
- A request with a missing, expired or wrong-type access token gets a 401 problem from the entry point bean (`SecurityConfig.java`); the frontend answers that 401 with one `/refresh` call and a retry. 403 is only for signed-in users lacking the role.
- Disabled and expired-credential messages are only returned after the password matches. A locked account answers with the generic message and its password is not checked.
- `RegisterUserUseCase` was injected but never called; it was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`. Registration stays disabled: `/register` always answers 403.
