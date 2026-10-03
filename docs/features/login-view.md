# LoginView + authStore

`frontend/src/views/LoginView.vue`, `frontend/src/stores/authStore.js`

Sign-in form at `/login` (guest-only, no minimum role: `frontend/src/router/index.js:43-46`) and the Pinia store that holds the session. Route guards and role table: [../authentication.md](../authentication.md).

## Endpoints used
Via `frontend/src/services/api.js` (`baseURL` `/api`, `:6`; `withCredentials`, `:16`). Backend: [auth-controller.md](auth-controller.md).

| Call | Request | Used by |
|------|---------|---------|
| `login` (`api.js:428`) | `POST /auth/login` | `authStore.login` |
| `logout` (`api.js:439`) | `POST /auth/logout` | `authStore.logout` |
| `refreshToken` (`api.js:448`) | `POST /auth/refresh` | interceptor, `authStore.refreshToken` |
| `getCurrentUser` (`api.js:466`) | `GET /users/me` | `authStore.checkAuth` |

## State
`authStore.js`:
- `user`, `isAuthenticated`: signed-in user `{email, role}` (`:11-12`)
- `isLoading` / `error`: exposed as `isAuthLoading` / `authError` (`:35-36`); `error` holds a ready-to-show message
- `authChecked`: set once `checkAuth` has run (even on failure); `clearAuth` does not reset it (`:203`)
- `lastActivity`, `sessionTimeout` (1 h, `:18`): inactivity clock
- `userRole`, `isAdmin`, `isStaff`, `isVolunteer`, `homePath` (`/dashboard` for VOLUNTEER+, else `/profile`, `:43`)

`LoginView.vue`: `form`, `errors` (per-field), `isFormValid` (`:111`). The page is a paper card with the woven band on top, on the mist background, with the Felege Selam wordmark above it and the note "Accounts are set up by the church office." below; there is no Sign up link.

## Actions
Store:
- `login(credentials)` (`:62`): validates email, lowercases it, calls api, sets `user`/`isAuthenticated`/`lastActivity`, starts the 30 s inactivity timer. Rethrows after setting `error`.
- `logout()` (`:136`): stops timer, calls api, always `clearAuth()` even if the call fails.
- `checkAuth()` (`:159`): runs once (cached by `authChecked`); `GET /users/me`; on any failure clears auth and returns null. Called by the router guard (`router/index.js:67`).
- `refreshToken()` (`:183`): calls api; on failure `clearAuth()` and rethrows.
- `hasRole(minRole)` (`:24`): rank compare `MEMBER < VOLUNTEER < STAFF < ADMIN` (`:21`); false when signed out or role unknown. Mirrors the backend hierarchy.
- `isSessionExpired()` / `getTimeUntilExpiry()` (`:46`, `:50`): plain functions (read the clock each call).
- `updateActivity()` (`:240`): bumps `lastActivity`; wired to mouse, key, scroll, touch by `initialize()` (`:280`).
- `startSessionMonitoring()` (`:246`): every 30 s, calls `logout()` if idle past `sessionTimeout`.
- `handleAuthError(err)` (`:210`): prefers ProblemDetail `detail`, else maps status to a message.
- `clearAuth()`, `clearError()`, `setSessionTimeout()`, `forceLogout()`, `register()` (see Gotchas).

View:
- `handleLogin` (`LoginView.vue:148`): clears error, validates (email format and a non-empty password, no minimum length, `:119-146`), calls `authStore.login`, toasts, then `router.push` to `?redirect` or `/` (`:182-196`). Failure toasts `authStore.authError` (`:197-205`).

## Interceptor (`api.js`, auth parts)
- Request: copies `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` on post/put/patch/delete (`:30-52`). The backend requires it on every write, login included, and sets the cookie on its first response; see CSRF in [../authentication.md](../authentication.md).
- Response 401 (`:105`): skips URLs containing `/login` or `/refresh` (`:109`); otherwise calls `refreshToken`, bumps activity, retries the request once (`_retry`, `:106`, `:126`).
- Refresh failure (`:127-170`): `clearAuth`, removes `user`/`auth_timestamp` from local/session storage, toasts "Session Expired" and hard-redirects to `/login?session=expired`, both only if the user was signed in (`:166`).
- Other 401 shows an "Unauthorized" toast (`:174-195`).

## Collaborators
- `frontend/src/services/api.js` (above), `frontend/src/stores/appStore` (`addNotification`), `vue-router`.
- Router guard: `router/index.js:75-77` logs out and redirects to `/login?session=expired` when `isSessionExpired()`; `:91-93` sends a signed-in user from `/login` to `homePath`.

## Errors
Shown in the form alert (`LoginView.vue:15`) and a toast.
- Client: "Email is required", "Please enter a valid email address", "Password is required" (`:119-137`).
- Server: ProblemDetail `detail` shown verbatim (`authStore.js:215`). Backend login failures are 400, so the status-based fallbacks (`:219-236`) apply only when there is no `detail` (e.g. network error -> `error.message`).

## Side effects
- `sessionStorage.auth_timestamp` set on login and refresh (`api.js:433`, `:453`); `user`/`auth_timestamp` removed on logout and refresh failure (`:442-444`, `:459-461`).
- Document-level activity listeners added by `initialize()` (never removed).
- 30 s `setInterval` while signed in.

## Gotchas
- Sessions renew: an expired access cookie gets a 401, the interceptor refreshes once and retries (`api.js:105-126`), so the 1 h client idle timeout is the limit. The "Access Denied" toast appears only for a real 403 (signed in, role too low), not for an expired session.
- The "Session Expired" toast on `LoginView` fires only after a successful sign-in (`LoginView.vue:186-193`), not on arrival at `/login?session=expired`.
- `clearErrorOnInput` is defined (`LoginView.vue:209`) but not bound to any input, so the error alert stays until the next submit.
- `authStore.register` posts to `/v1/auth/register` (`authStore.js:113`), which becomes `/api/v1/auth/register`, not `/api/auth/register`; registration is disabled server-side anyway.
- A locked account shows the same generic message as any failed sign-in (the lock ends after 15 minutes); too many failures give a 429 whose message the form shows.
