# Authentication and Authorization

*Last checked against the code: 3 October 2026, branch `feature/role-auth` plus working tree.*

How users sign in, how sessions are kept, and which role can call which endpoint. Backend and frontend are both covered here.

## Design decision

- **Status:** accepted (first written 5 September 2025)
- **Decision:** stateless JWTs carried in two HttpOnly cookies, `sid` (access) and `sid_refresh` (refresh). The `Authorization: Bearer` header is still accepted as a fallback for mobile or legacy clients.
- **Why:** keeps tokens out of JavaScript, needs no server session store, and fits the clean-architecture layering.
- **Mobile (out of scope for now):** native clients should keep using the Bearer header, store tokens in Keychain or Keystore, and need no CSRF protection.

## Backend

### Endpoints

All paths are under `/api`. Only `/api/auth/**` is public; everything else requires a valid access token.

| Method | Path | What it does |
|--------|------|--------------|
| POST | `/api/auth/login` | Checks email and password, sets both cookies |
| POST | `/api/auth/refresh` | Reads `sid_refresh`, issues new cookies |
| POST | `/api/auth/logout` | Clears both cookies |
| POST | `/api/auth/register` | Always returns 403: self-registration is disabled |
| GET | `/api/users/me` | Current user |
| PUT | `/api/users/me/profile` | Update own profile |
| PUT | `/api/users/me/password` | Change own password |

Registration was disabled in February 2026 (commit `5356063`). There is no admin API for users yet, so accounts are created in SQL. Seed users are in `src/main/resources/db/sql/002.sample-data.sql`.

### Cookies

| Cookie | Holds | Path | Lifetime | Property |
|--------|-------|------|----------|----------|
| `sid` | Access JWT | `/` | 30 min | `auth.access-ttl-seconds` (`ACCESS_TTL`) |
| `sid_refresh` | Refresh JWT | `/v1/auth` | 30 days | `auth.refresh-ttl-seconds` (`REFRESH_TTL`) |

> **Known bug:** the refresh cookie's path is `/v1/auth`, but the endpoint is `/api/auth/refresh`, so the browser never sends the cookie and sessions end after 30 minutes. See C5 in [functionality-audit.md](functionality-audit.md).

`JwtAuthenticationFilter` reads the `sid` cookie first, then falls back to the `Authorization: Bearer` header.

### Configuration

In `src/main/resources/application.properties`:

```properties
auth.cookies.enabled=true
auth.cookies.access-name=sid
auth.cookies.refresh-name=sid_refresh
auth.cookies.secure=${COOKIE_SECURE:false}      # set true in production
auth.cookies.same-site=${COOKIE_SAMESITE:Lax}   # Lax | Strict | None
auth.cookies.domain=${COOKIE_DOMAIN:}           # e.g. .example.com for subdomains
auth.access-ttl-seconds=${ACCESS_TTL:1800}
auth.refresh-ttl-seconds=${REFRESH_TTL:2592000}
auth.jwt-secret=${JWT_SECRET:...}               # the default is for development only
```

CORS allows credentials, but the allowed origins are hard-coded in two places: `SecurityConfig.corsConfigurationSource()` (`localhost:3000`, `localhost:8080`) and `WebMvcConfig.addCorsMappings()` (`localhost:8080`, `8081`, `8082`). Both need changing for production.

### Passwords and lockout

- Passwords are hashed with BCrypt, cost 12.
- A new password must be at least 8 characters with an uppercase letter, a lowercase letter, a digit and one of `@$!%*?&` (`User.validatePasswordStrength`).
- Five failed logins lock the account. Nothing unlocks it automatically.

### CSRF

CSRF protection is **disabled** (`SecurityConfig`). Same-site cookies (`SameSite=Lax`) are the only protection today.

The frontend is already prepared: it copies an `XSRF-TOKEN` cookie into an `X-XSRF-TOKEN` header on every non-GET request. To turn protection on:

```java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
```

If the frontend and API ever run on different sites, use `SameSite=None; Secure` and CSRF protection together.

## Roles and permissions

Four roles, from `UserRole`. Each role includes everything below it (`RoleHierarchy` bean in `SecurityConfig`):

**ADMIN** > **STAFF** > **VOLUNTEER** > **MEMBER**

The old `USER` role was removed. Migration `004.migrate-user-role-to-member.sql` converts existing `USER` rows to `MEMBER`.

| Area | VOLUNTEER and above | STAFF and above | ADMIN only |
|------|--------------------|-----------------|------------|
| Members `/api/members` | List, get, active, inactive, overdue, export, export selected | Create, update | Delete |
| Payments `/api/payments` | List, get, by member, export | Record | Delete |
| Communications `/api/communications` | List, get, deliveries | Create, send to all, send to overdue, retry failed delivery | |
| Dashboard `/api/dashboard/*` | All | | |
| Own account `/api/users/me*` | Any signed-in user, including MEMBER | | |

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
- **403:** no retry.
- **Network errors and 5xx:** retried after a delay. `VITE_API_RETRY_ATTEMPTS` (default 3) is meant to set the limit, but the `_retry` flag stops it after one retry.
- Adds the `X-XSRF-TOKEN` header described under [CSRF](#csrf).

### Route guards (`router/index.js`)

| Route meta | Effect |
|------------|--------|
| `requiresAuth: true` | Signed-out users go to `/login?redirect=<path>` |
| `requiresGuest: true` | Signed-in users go to their home page (used by `/` and `/login`) |
| `requiresRole: 'VOLUNTEER'` | Minimum role, using the same hierarchy as the backend. Users without it go to their home page (`/dashboard`, or `/profile` for MEMBER) with `?error=access_denied` |

Dashboard, members, payments and communications require VOLUNTEER; `/profile` is open to every signed-in user.

## Manual test

```bash
# Log in and save cookies
curl -i -c jar.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@membertracker.com","password":"<password>"}'

curl -b jar.txt http://localhost:8080/api/users/me                       # expect 200
curl -i -b jar.txt -c jar.txt -X POST http://localhost:8080/api/auth/refresh
curl -i -b jar.txt -X POST http://localhost:8080/api/auth/logout          # then /users/me returns 401
```

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Login returns 401 | Backend running; email and password correct; the account is not locked (`users.account_non_locked`) |
| Logged out after about 30 minutes | Expected until the refresh-cookie path bug (C5) is fixed |
| Logged out sooner | The client-side 1-hour inactivity timeout, or the backend restarted with a different `JWT_SECRET` |
| Redirect loop on load | `authStore.initialize()` must run in `App.vue` so `authChecked` gets set |
| CORS error in the browser | The frontend origin must be in both CORS lists (see [Configuration](#configuration)) |

## Known gaps

Full list and fixes in [functionality-audit.md](functionality-audit.md) (C5, C6) and [todo.md](todo.md).

- The refresh cookie is never sent (path bug), and refresh and access tokens are interchangeable (no type claim)
- Login error messages reveal whether an email exists
- Logout does not revoke tokens, and refresh tokens are not rotated
- No password reset, MFA, "remember me", or session list
- Locked accounts never unlock automatically
- Sessions are not synchronised across browser tabs
