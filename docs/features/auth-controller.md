# AuthController

`src/main/java/io/github/membertracker/infrastructure/AuthController.java`

Public sign-in, refresh and sign-out endpoints that issue and clear the `sid` / `sid_refresh` JWT cookies. Cookie, CSRF and lockout details: [../authentication.md](../authentication.md).

## Endpoints
All under `/api/auth`. No `@PreAuthorize`; `/api/auth/**` is `permitAll` (`infrastructure/config/SecurityConfig.java:81-82`).

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| POST | `/login` (`AuthController.java:50`) | public | `LoginRequest {email, password}` (`:150`) | 200 `{user: {email, role}}` + 2 `Set-Cookie`; role is the first authority minus `ROLE_` |
| POST | `/register` (`:84`) | public | `RegisterRequest` (`:171`), ignored | always 403 |
| POST | `/refresh` (`:91`) | public (needs `sid_refresh` cookie) | none | 204 + 2 new `Set-Cookie` |
| POST | `/logout` (`:124`) | public | none | 204 + 2 clearing `Set-Cookie` (Max-Age 0) |

## Actions
- `login` -> `AuthenticateUserUseCase.invoke(email, password)`, then signs access + refresh JWTs and builds both cookies (`:55-61`)
- `refresh` -> reads `sid_refresh` (`getRefreshTokenFromCookie`, `:139`), `JwtUtils.validateToken`, loads user by token subject, issues a fresh pair (`:96-109`). Rotation only; the old refresh token is not revoked.
- `logout` -> only clears cookies (`:129-130`); tokens stay valid until they expire.
- `register` -> returns 403 without touching any use case (`:88`).

## Collaborators
- `usecase/AuthenticateUserUseCase.java:18`: lookup by email, BCrypt match, then checks enabled, locked, credentials non-expired; resets failed-attempt counter on success.
- `usecase/LoadUserByUsernameUseCase.java`: used by `refresh` (`AuthController.java:106`).
- `utils/CookieUtils.java:16,28,40,47`: builds access, refresh and clearing cookies from `AuthProperties`.
- `utils/JwtUtils.java:52,57,78`: HS256 token generation and validation. Access and refresh tokens carry only subject + timestamps.
- `infrastructure/config/AuthProperties.java:8`: `auth.*` (TTLs `:11-12`, cookie names/flags `:15-21`, secret `:13`).
- `infrastructure/filter/JwtAuthenticationFilter.java:39`: validates `sid` cookie (then Bearer header, `:63-72`) on all other endpoints.
- Models: `domain/model/User` (returned by the use case; only email + role leave the controller).

## Errors
RFC 7807 via `ProblemDetails.of` (format: [../architecture.md](../architecture.md)).

| Status | Source |
|--------|--------|
| 400 | `login`: any `RuntimeException` from the use case, message passed through as `detail` (`:79-80`) |
| 400 | `refresh`: no cookie (`:98`), invalid or expired token (`:102`), any other failure (`:120`) |
| 403 | `register`: "Registration is disabled. Please contact administrator for access." (`:88`) |

Login failures are 400, not 401, and refresh failures are 400.

## Side effects
- Login with a wrong password increments the failed-attempt counter and saves the user (`AuthenticateUserUseCase.java:22-25`); the fifth failure locks the account (`domain/model/User.java:218-219`).
- Successful login resets the counter and saves (`AuthenticateUserUseCase.java:40-41`).
- No emails, no jobs.

## Gotchas
- Refresh cookie path is `/v1/auth` (`CookieUtils.java:33`, `:50`) but the endpoint is `/api/auth/refresh`, so the browser never sends it and `/refresh` returns 400 "Refresh token not found" (audit C5).
- Access and refresh tokens are interchangeable: no type claim (`JwtUtils.java:52-60`), filter does not distinguish them (audit C5).
- Login reveals which emails exist: unknown email gives "User with email '...' not found" (`domain/exception/UserDomainException.java:58-61`), wrong password gives "Invalid password provided" (`:37-40`) (audit C6).
- Disabled, locked and expired-credential messages are only returned after the password matches, so they also confirm a valid password (`AuthenticateUserUseCase.java:28-38`).
- `RegisterUserUseCase` is injected (`AuthController.java:44`) but never called.
