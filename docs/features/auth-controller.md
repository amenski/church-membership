# AuthController

`src/main/java/io/github/membertracker/infrastructure/AuthController.java`

Public sign-in, refresh and sign-out endpoints that issue and clear the `sid` / `sid_refresh` JWT cookies. Cookie, CSRF and lockout details: [../authentication.md](../authentication.md).

## Endpoints
All under `/api/auth`. No `@PreAuthorize`; `/api/auth/**` is `permitAll` (`infrastructure/config/SecurityConfig.java:125-126`). CSRF still applies: every POST below needs the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie, otherwise 403 problem "Invalid or missing CSRF token" (the cookie is set on any earlier response, e.g. `GET /api/users/me`).

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| POST | `/login` (`AuthController.java:51`) | public | `LoginRequest {email, password}` (`:151`) | 200 `{user: {email, role}}` + 2 `Set-Cookie`; role is the first authority minus `ROLE_` |
| POST | `/register` (`:85`) | public | `RegisterRequest` (`:172`), ignored | always 403 |
| POST | `/refresh` (`:92`) | public (needs `sid_refresh` cookie) | none | 204 + 2 new `Set-Cookie` |
| POST | `/logout` (`:125`) | public | none | 204 + 2 clearing `Set-Cookie` (Max-Age 0) |

## Actions
- `login` -> `AuthenticateUserUseCase.invoke(email, password)`, then signs access + refresh JWTs and builds both cookies (`:56-62`)
- `refresh` -> reads the refresh cookie named by `auth.cookies.refresh-name` (`getRefreshTokenFromCookie`, `:140`), `JwtUtils.validateToken(.., "refresh")` (`:102`), loads user by token subject, issues a fresh pair (`:97-110`). Rotation only; the old refresh token is not revoked.
- `logout` -> only clears cookies (`:130-131`); tokens stay valid until they expire.
- `register` -> returns 403 without touching any use case (`:89`).

## Collaborators
- `usecase/AuthenticateUserUseCase.java:19`: lookup by email; for an unknown email a dummy BCrypt check (`:24`, hash memoised in `dummyHash()` `:52`) then `invalidCredentials()`; BCrypt match, then checks enabled, locked, credentials non-expired; resets failed-attempt counter on success.
- `usecase/LoadUserByUsernameUseCase.java`: used by `refresh` (`AuthController.java:107`).
- `utils/CookieUtils.java:16,28,40,47`: builds access, refresh and clearing cookies from `AuthProperties`.
- `utils/JwtUtils.java:56,62,98`: HS256 token generation and typed validation. Tokens carry subject, timestamps and a `typ` claim (`access` / `refresh`).
- `infrastructure/config/AuthProperties.java:8`: `auth.*` (TTLs `:11-12`, cookie names/flags `:15-21`, secret `:13`).
- `infrastructure/filter/JwtAuthenticationFilter.java:39`: accepts only `access` tokens (`:43`) from the `sid` cookie, then the Bearer header (`:63-72`), on all other endpoints.
- Models: `domain/model/User` (returned by the use case; only email + role leave the controller).

## Errors
RFC 7807 via `ProblemDetails.of` (format: [../architecture.md](../architecture.md)).

| Status | Source |
|--------|--------|
| 400 | `login`: `UserDomainException` from the use case, message passed through as `detail` (`:80-81`); any other exception goes to `GlobalExceptionHandler` (500, generic message) |
| 400 | `refresh`: no cookie (`:99`), invalid, expired or wrong-type token (`:102`), any other failure (`:121`) |
| 403 | `register`: "Registration is disabled. Please contact administrator for access." (`:89`) |

Login failures are 400, not 401, and refresh failures are 400. Unknown email and wrong password return the same message, "Invalid email or password" (`UserDomainException.java:45-50`).

## Side effects
- Login with a wrong password increments the failed-attempt counter and saves the user (`AuthenticateUserUseCase.java:28-32`); the fifth failure locks the account (`domain/model/User.java:218-219`).
- Successful login resets the counter and saves (`AuthenticateUserUseCase.java:46-47`).
- No emails, no jobs.

## Gotchas
- Tokens issued before the `typ` claim existed are rejected by both the filter and `/refresh`, so everyone signed in again once after that deploy.
- A request with a missing, expired or wrong-type access token gets a 401 problem from the entry point bean (`SecurityConfig.java:83-92`); the frontend answers that 401 with one `/refresh` call and a retry. 403 is only for signed-in users lacking the role.
- Disabled, locked and expired-credential messages are only returned after the password matches, so a locked account's message appears only after the correct password (`AuthenticateUserUseCase.java:34-44`).
- The lockout counter only counts failures for existing accounts (by design: there is nothing to count for an unknown email).
- `RegisterUserUseCase` is injected (`AuthController.java:45`) but never called.
