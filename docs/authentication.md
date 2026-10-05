# Authentication and Authorization

*Last checked against the code: 5 October 2026, branch `main`.*

How users sign in, how sessions are kept, and which role can call which endpoint. Backend and frontend are both covered here.

## Design decision

- **Status:** accepted (first written 5 September 2025)
- **Decision:** stateless JWTs carried in two HttpOnly cookies, `sid` (access) and `sid_refresh` (refresh). The `Authorization: Bearer` header is still accepted as a fallback for mobile or legacy clients.
- **Why:** keeps tokens out of JavaScript, needs no server session store, and fits the clean-architecture layering.
- **Mobile (out of scope for now):** native clients should keep using the Bearer header, store tokens in Keychain or Keystore, and are not exposed to cookie-based CSRF, but the server still requires the `X-XSRF-TOKEN` header on writes (read the `XSRF-TOKEN` cookie from any response and echo it).

## Backend

### Public paths

Without a sign-in the app answers only these, so the browser can load the sign-in page:

| Path | Why |
|------|-----|
| `/api/auth/**` | Sign-in, refresh, logout, register (403) |
| `/` | The welcome path; it serves `/index.html` |
| `/index.html` | The single-page app. `Cache-Control: no-cache` |
| `/assets/**` | The built scripts, styles and fonts. Their names carry a content hash, so `Cache-Control: max-age=31536000, public, immutable`. A missing file answers 404 |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Permitted, but only the dev profile serves them |

Client routes such as `/login`, `/members` and `/members/3` are not on the list: `SpaFallbackFilter` (in the security chain, before the authorization check) forwards a GET or HEAD request to `/index.html` when the path is not under `/api`, `/v3`, `/swagger-ui` or `/error`, has no file extension in its last segment, and the client accepts text/html (or sends no Accept header, or `*/*`). The Vue router and the app's own guards then decide what the user sees. Everything else keeps the rule: a signed-out request for `/api/...` (known or not) or any other path, such as `/favicon-x.png`, answers 401 `application/problem+json`, and a signed-in request for a path that does not exist answers 404. The page response carries the security headers and the `XSRF-TOKEN` cookie, so the cookie exists before the first API call.

### Endpoints

All paths are under `/api`. Only `/api/auth/**` is public; everything else requires a valid access token. The web pages the jar serves are public too, see [Public paths](#public-paths).

| Method | Path | What it does |
|--------|------|--------------|
| POST | `/api/auth/login` | Checks email and password, sets both cookies |
| POST | `/api/auth/refresh` | Reads `sid_refresh`, issues new cookies |
| POST | `/api/auth/logout` | Clears both cookies |
| POST | `/api/auth/register` | Always returns 403: self-registration is disabled |
| GET | `/api/users/me` | Current user |
| PUT | `/api/users/me/profile` | Update own profile |
| PUT | `/api/users/me/password` | Change own password |

Registration was disabled in February 2026 (commit `5356063`). There is no admin API for users yet, so accounts are created in SQL, except the first one:

**First administrator.** A production database has no users (the sample users in `002.sample-data.sql` are loaded only by the `dev` profile, through the Liquibase context `dev`). At startup `BootstrapAdminRunner` calls `BootstrapFirstAdminUseCase` with `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (`app.bootstrap.admin-email` and `app.bootstrap.admin-password`, empty by default). The rule:

- It acts only while the users table is empty. If any user exists it does nothing, so the variables can stay in place, but the operator removes `BOOTSTRAP_ADMIN_PASSWORD` after the first start.
- It creates one enabled `ADMIN`, with the password BCrypt-hashed by the same encoder as every other password (cost 12).
- The email must be valid (`Email.of`) and the password must meet the password rule below (`User.validatePasswordStrength`). An invalid value, or only one of the two variables set, stops startup with a message naming the variable; the password is never in a message or a log. The only log line is `Created the first administrator <email>`.
- It writes no activity-log entry: that needs a new activity type and a frontend label.

Steps are in [development.md](development.md#first-start).

### Cookies

| Cookie | Holds | Path | Lifetime | Property |
|--------|-------|------|----------|----------|
| `sid` | Access JWT | `/` | 30 min | `auth.access-ttl-seconds` (`ACCESS_TTL`) |
| `sid_refresh` | Refresh JWT | `/api/auth` | 30 days | `auth.refresh-ttl-seconds` (`REFRESH_TTL`) |

### Token types

Every token carries a `typ` claim: `access` or `refresh` (`JwtUtils.TOKEN_TYPE_ACCESS`, `TOKEN_TYPE_REFRESH`). `JwtAuthenticationFilter` accepts only `access` tokens and `POST /api/auth/refresh` accepts only `refresh` tokens, so a 30-day refresh token cannot be used as an access token. Tokens issued before this change carry no `typ` and are rejected, so everyone signs in again once after deploy.

Both token types also carry `iat` (issue time) and `auth_time` (epoch seconds of the original sign-in). Rotation keeps `auth_time`: a refresh token expires at `auth_time + refresh TTL` (30 days), so refreshing never extends a session beyond 30 days from sign-in, and the rotated refresh cookie only lives for the time that is left. A refresh token without `auth_time` (issued before this change) is rejected by `/api/auth/refresh` (the user signs in again once); access tokens without it still work until they expire. A token whose `iat` is in an earlier second than the user's `lastPasswordChange` is rejected by the filter (401) and by `/api/auth/refresh` (400); no `lastPasswordChange` means no check.

`JwtAuthenticationFilter` reads the `sid` cookie first, then falls back to the `Authorization: Bearer` header.

A valid access token for a user who no longer exists, or who is disabled, locked or has expired credentials, is treated as signed out: the filter logs it at DEBUG, leaves the request unauthenticated, and protected paths answer 401. Sign-in, refresh and logout (public paths) still work with that stale cookie.

### Configuration

In `src/main/resources/application.properties`:

```properties
auth.cookies.access-name=sid
auth.cookies.refresh-name=sid_refresh
auth.cookies.secure=${COOKIE_SECURE:true}       # true by default; the dev profile sets false for local http
auth.cookies.same-site=${COOKIE_SAMESITE:Lax}   # Lax | Strict | None
auth.cookies.domain=${COOKIE_DOMAIN:}           # e.g. .example.com for subdomains
auth.access-ttl-seconds=${ACCESS_TTL:1800}
auth.refresh-ttl-seconds=${REFRESH_TTL:2592000}
auth.jwt-secret=${JWT_SECRET}                   # required, no default, at least 32 characters (the dev profile sets a local-only one)
```

Cookies are `Secure` unless `COOKIE_SECURE=false`; the `dev` profile sets it to `false` so sign-in works over local http. The app also listens on `127.0.0.1` only by default (`server.address=${SERVER_ADDRESS:127.0.0.1}`), so it is reachable through the reverse proxy and not directly.

CORS allows credentials, but the allowed origins are hard-coded in two places: `SecurityConfig.corsConfigurationSource()` (`localhost:3000`, `localhost:8080`) and `WebMvcConfig.addCorsMappings()` (`localhost:8080`, `8081`, `8082`). Both need changing for production.

### Passwords and lockout

- Passwords are hashed with BCrypt, cost 12.
- A new password must be 8 characters or more and at most 72 UTF-8 bytes (BCrypt ignores anything longer), with an uppercase letter, a lowercase letter, a digit and a special character (`User.validatePasswordStrength`). Special means any character that is not a letter, digit or whitespace, so `-`, `_`, `#` and `.` count; spaces are allowed but do not count as special. The web form checks the same rule (`frontend/src/utils/passwordRules.js`).
- When the user changes their password the current one must be given and match; there is no minimum length on it, so an older, shorter password (such as the dev sample admin's) can be replaced. A wrong current password is a 400 "Current password is incorrect"; a weak new one is a 400 with the rule text.
- Changing the password ends every other session: tokens issued before the change are rejected (see the token claims above). `PUT /api/users/me/password` answers with fresh `sid` and `sid_refresh` cookies, so the browser that changed the password stays signed in. The change time is stored in whole seconds, as token issue times are.
- Five failed attempts lock the account for 15 minutes, and the lock ends by itself (`users.locked_until`, changeset 006). A lock with no `locked_until` (set by hand or an old row) stays permanent until someone clears `account_non_locked` in the database.
- A wrong current password on a password change counts toward the same lock as a wrong sign-in password.
- The lock is checked before the password. A locked account is never password-checked, and sign-in answers an unknown email, a wrong password and a locked account with the same 400 and the same text, "Invalid email or password. After several failed attempts an account is locked for 15 minutes.", so a lock cannot be used to find out a password or to tell which emails exist.
- The attempt counter, its reset and the password update are single-row SQL updates (`UserRepository.recordFailedLogin`, `resetFailedLogins`, `updatePassword`); sign-in never saves the whole user, so 50 parallel guesses are all counted and a slow BCrypt check cannot overwrite a concurrent password change.
- `LoginAttemptLimiter` (in memory, one process) counts failed sign-ins in a sliding 10-minute window: 10 per email (case-insensitive) and 30 per client IP. Over the limit, `POST /api/auth/login` answers 429 "Too many sign-in attempts. Try again in a few minutes." with a `Retry-After` header (seconds). Successes are never counted, and a success clears the email's entries. A restart resets the counters.
- The client IP is `request.getRemoteAddr()`. Tomcat replaces it with the first `X-Forwarded-For` address only when the direct peer is a trusted proxy (`server.tomcat.remoteip.remote-ip-header=X-Forwarded-For`, `internal-proxies` limited to loopback by `TRUSTED_PROXIES`), so a client cannot choose its own IP. The per-email limit and the lock do not depend on the IP.

### CSRF

CSRF protection is **on** (`SecurityConfig.filterChain`), because the browser sends the `sid` cookie automatically.

- Spring uses `CookieCsrfTokenRepository.withHttpOnlyFalse()` with the plain `CsrfTokenRequestAttributeHandler`, so the raw cookie value is what the header must carry. The `XSRF-TOKEN` cookie has `Path=/`, the same `SameSite` and `Secure` settings as the auth cookies, and is readable by JavaScript.
- `CsrfCookieFilter` reads the token on every request, so the cookie is written on every response, including 401 and GET ones. The browser gets it from its first call (`GET /api/users/me`).
- The token cookie is kept for the whole browser session: rotation on authentication is disabled (`NullAuthenticatedSessionStrategy`) because the API is stateless. With the default strategy the cookie was cleared on every JWT-authenticated request and about every second write got 403.
- `POST`, `PUT`, `PATCH` and `DELETE` need an `X-XSRF-TOKEN` header equal to the cookie. No endpoint is exempt: login, refresh and logout need it too. GET, HEAD and OPTIONS do not.
- A missing or wrong token gets 403 `application/problem+json` with detail "Invalid or missing CSRF token" (`SecurityConfig.accessDeniedHandler`). Any other filter-level denial answers 403 "Access denied".
- The web app already echoes the cookie into the header (`api.js`), and CORS allows the `x-xsrf-token` header. Other clients must read the `XSRF-TOKEN` cookie and send it back as the header.

If the frontend and API ever run on different sites, use `SameSite=None; Secure`; the cookie and header scheme stays the same.

## Roles and permissions

Four roles, from `UserRole`. Each role includes everything below it (`RoleHierarchy` bean in `SecurityConfig`):

**ADMIN** > **STAFF** > **VOLUNTEER** > **MEMBER**

The old `USER` role was removed. Migration `004.migrate-user-role-to-member.sql` converts existing `USER` rows to `MEMBER`.

| Area | VOLUNTEER and above | STAFF and above | ADMIN only |
|------|--------------------|-----------------|------------|
| Members `/api/members` | List, get, active, inactive, overdue | Create, update, export, export selected | Delete (archives, see below), delete for good (`/{id}/permanent`), `?archived=true` list, read and export of archived members |
| Households `/api/households` | List, get | Create, update | Delete |
| People `/api/people` | List, get | Create, update, start a membership | Delete |
| Payments `/api/payments` | List, page, paid-months, summary, get, by member | Record, export | |
| Communications `/api/communications` | List, get, deliveries | Send to all, send to one member, send to overdue, retry failed delivery | |
| Dashboard `/api/dashboard/*` | All | | |
| Activity log `/api/activity-log` | | | View the activity log (who changed or exported what) |
| Own account `/api/users/me*` | Any signed-in user, including MEMBER | | |
| Own dues `/api/me/dues` | Any signed-in user, including MEMBER (matched to a member by email, see [features/my-dues.md](features/my-dues.md)) | | |

**Archived members are visible to ADMIN only, on every read path** (`infrastructure/security/ArchivedVisibility`, one helper used by all controllers): `GET /api/members/{id}`, `GET /api/payments/member/{id}` and `POST /api/communications/send-to-member/{id}` answer as for an unknown id (404, 404, 400 `MEMBER_006`), the id-based export drops them, and where a payment or a delivery embeds an archived member (`/api/payments`, `/api/payments/{id}`, `/api/communications/{id}/deliveries`, delivery retry) the email and phone are blanked and the name stays so the history still reads.

## Frontend

Files: `frontend/src/stores/authStore.js`, `frontend/src/services/api.js`, `frontend/src/router/index.js`, `frontend/src/views/LoginView.vue`.

### Auth store (`authStore.js`)

| Kind | Names |
|------|-------|
| Actions | `hasRole(minRole)`, `isSessionExpired()`, `getTimeUntilExpiry()`, `login({ email, password })`, `logout()`, `checkAuth()`, `refreshToken()`, `forceLogout()`, `updateActivity()`, `setSessionTimeout(ms)`, `initialize()` |
| State | `user`, `isAuthenticated`, `isLoading`, `error`, `authChecked`, `lastActivity`, `sessionTimeout` |
| Getters | `currentUser`, `isLoggedIn`, `userRole`, `isAdmin`, `isStaff`, `isVolunteer`, `homePath`, `authError` |

Use the store's actions; don't change auth state directly.

```js
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
await authStore.login({ email, password })   // error text ends up in authStore.authError
if (authStore.isAdmin) { /* ... */ }
await authStore.logout()
```

**Client-side session timeout:** 1 hour without activity (`sessionTimeout`). Activity is mouse, keyboard, scroll, touch, or any API request. The store checks for expiry every 30 seconds, then logs out and sends the user to `/login?session=expired`.

### API client (`api.js`)

- Axios with `withCredentials: true`, so cookies go with every request. Base URL: `VITE_API_BASE_URL`, default `/api`.
- **401:** calls refresh once, then retries the original request. If refresh fails, it clears auth state and redirects to login.
- **403:** no retry. A signed-in user whose role is too low gets 403, and so does a write without a valid CSRF token; a missing, expired or wrong-type access token gets a 401 `application/problem+json` (`SecurityConfig.authenticationEntryPoint`), which is what triggers the refresh. The CSRF 403 is not retried.
- **Network errors and 5xx:** retried after a delay. `VITE_API_RETRY_ATTEMPTS` (default 3) is meant to set the limit, but the `_retry` flag stops it after one retry.
- Adds the `X-XSRF-TOKEN` header described under [CSRF](#csrf).

### Route guards (`router/index.js`)

| Route meta | Effect |
|------------|--------|
| `requiresAuth: true` | Signed-out users go to `/login?redirect=<path>` |
| `requiresGuest: true` | Signed-in users go to their home page (used by `/` and `/login`) |
| `requiresRole: 'VOLUNTEER'` | Minimum role, using the same hierarchy as the backend. Users without it see an "Access denied" warning notification and go to their home page (`/dashboard`, or `/my-dues` for MEMBER) with no query parameter |

Dashboard, members (and a member's page), households, payments, communications and More require VOLUNTEER; `/activity` requires ADMIN; `/profile` and `/my-dues` are open to every signed-in user.

## Manual test

Writes need the CSRF header, so fetch the token cookie first and echo it.

```bash
# First GET sets the XSRF-TOKEN cookie (a 401 here is fine)
curl -s -c jar.txt http://localhost:8080/api/users/me > /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' jar.txt)

# Log in and save cookies
curl -i -b jar.txt -c jar.txt -X POST http://localhost:8080/api/auth/login \
  -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"email":"admin@membertracker.com","password":"<password>"}'

curl -b jar.txt http://localhost:8080/api/users/me                       # expect 200
curl -i -b jar.txt -c jar.txt -X POST -H "X-XSRF-TOKEN: $XSRF" http://localhost:8080/api/auth/refresh
curl -i -b jar.txt -X POST -H "X-XSRF-TOKEN: $XSRF" http://localhost:8080/api/auth/logout   # then /users/me returns 401
```

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Login fails | A failed login returns 400 with "Invalid email or password. After several failed attempts an account is locked for 15 minutes." (the same for an unknown email, a wrong password and a locked account), not 401. A 401 comes from protected endpoints called without a valid session. Check: backend running; email and password correct; the account is not locked (`users.account_non_locked`, `users.locked_until`): a lock ends 15 minutes after the fifth failure. To unlock at once: `UPDATE users SET account_non_locked = TRUE, failed_login_attempts = 0, locked_until = NULL WHERE email = '...'`. A 429 means the throttle (10 failures per email or 30 per IP in 10 minutes) tripped: wait for `Retry-After`, or restart the app |
| Logged out after about 30 minutes | Should not happen while active: an expired access cookie gets a 401 and the client refreshes. Check that the browser sends `sid_refresh` to `/api/auth/refresh` (cookie path `/api/auth`) and that `JWT_SECRET` did not change |
| Logged out sooner | The client-side 1-hour inactivity timeout, or the backend restarted with a different `JWT_SECRET` |
| Redirect loop on load | `authStore.initialize()` must run in `App.vue` so `authChecked` gets set |
| 403 "Invalid or missing CSRF token" on a write | The `XSRF-TOKEN` cookie was not sent or the header does not match (clients other than the web app must read the cookie and echo it). If it happens on every second write, check that no response carries `Set-Cookie: XSRF-TOKEN=; Max-Age=0` |
| CORS error in the browser | The frontend origin must be in both CORS lists (see [Configuration](#configuration)) |

## Known gaps

Full list and fixes in [functionality-audit.md](functionality-audit.md) and [todo.md](todo.md).

- Logout does not revoke tokens: a stolen refresh cookie works until the password changes or 30 days after sign-in (refresh tokens are rotated, but the old one stays valid until it expires)
- No password reset, MFA, "remember me", or session list
- The sign-in throttle is in memory (one process, reset on restart); a permanent lock (no `locked_until`) still needs a database edit
- Sessions are not synchronised across browser tabs
