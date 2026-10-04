# LoginView + authStore

`frontend/src/views/LoginView.vue`, `frontend/src/stores/authStore.js`

Sign-in form at `/login` (guest-only, no minimum role: `frontend/src/router/index.js:49-52`) and the Pinia store that holds the session. Route guards and role table: [../authentication.md](../authentication.md).

## Endpoints used
Via `frontend/src/services/api.js` (`baseURL` `/api`, `:6`; `withCredentials`, `:16`). Backend: [auth-controller.md](auth-controller.md).

| Call | Request | Used by |
|------|---------|---------|
| `login` (`api.js:409`) | `POST /auth/login` | `authStore.login` |
| `logout` (`api.js:420`) | `POST /auth/logout` | `authStore.logout` |
| `refreshToken` (`api.js:429`) | `POST /auth/refresh` | interceptor |
| `getCurrentUser` (`api.js:447`) | `GET /users/me` | `authStore.checkAuth` |

## State
`authStore.js`:
- `user`, `isAuthenticated`: signed-in user `{email, role}` (`:11-12`)
- `isLoading` / `error`: exposed as `isAuthLoading` / `authError` (`:35-36`); `error` holds a ready-to-show message
- `authChecked`: set once `checkAuth` has run (even on failure); `clearAuth` does not reset it (`:203`)
- `lastActivity`, `sessionTimeout` (1 h, `:18`): inactivity clock
- `userRole`, `isAdmin`, `isStaff`, `isVolunteer`, `homePath` (`/dashboard` for VOLUNTEER+, else `/my-dues`)

`LoginView.vue`: `form`, `errors` (per-field), `isFormValid`. The page root sets `data-density="comfortable"` and is a split layout from `lg` (992px):
- **Left half** (`rail` colour, white text): at the top a decorative year strip (`aria-hidden`: twelve squares, ten solid in `rail-accent`, one hatched, one outlined, reusing `SQUARES.missed` and `SQUARES.due` from `utils/yearStrip.js`) with the line "One square a month. Solid is paid, hatched is behind, an outline is due now."; at the bottom the congregation name in Amharic (56px, 72px from `xl`), "Felege Selam", and "The church office keeps members, dues, payments and messages in one place." Text is `rail-text` or white (10.87 and 14.52 on the rail). Nothing in the panel takes focus, so the focus order is still Email, Password, Sign in.
- **Right half**: the form card (paper, `max-w-sm`) centred vertically, with "Accounts are set up by the church office." below it. There is no Sign up link.
- **Below `lg`**: the panel shrinks to a short header (Amharic name 36px over "Felege Selam"; the strip and the two lines are hidden), then the card at the top of the mist ground. The two inputs and the button are 48px high (`max-lg:min-h-12`), inputs 16px so iOS does not zoom.

The fields are `BaseInput`, the alert is `AlertBanner`, the button is `BaseButton`, styled with Tailwind utilities. The shell (rail, top bar) is not rendered while signed out, so the page owns the whole screen.

## Actions
Store:
- `login(credentials)` (`:60`): validates email, lowercases it, calls api, sets `user`/`isAuthenticated`/`lastActivity`, starts the 30 s inactivity timer. Rethrows after setting `error`.
- `logout()` (`:100`): stops timer, calls api, always `clearAuth()` even if the call fails.
- `checkAuth()` (`:123`): runs once (cached by `authChecked`); `GET /users/me`; on any failure clears auth and returns null. Called by the router guard (`router/index.js:73`).
- `hasRole(minRole)` (`:23`): rank compare `MEMBER < VOLUNTEER < STAFF < ADMIN` (`:20`); false when signed out or role unknown. Mirrors the backend hierarchy.
- `isSessionExpired()` / `getTimeUntilExpiry()` (`:44`, `:48`): plain functions (read the clock each call).
- `updateActivity()` (`:190`): bumps `lastActivity`; wired to mouse, key, scroll, touch by `initialize()` (`:223`).
- `startSessionMonitoring()` (`:196`): every 30 s, calls `logout()` if idle past `sessionTimeout`.
- `handleAuthError(err)` (`:160`): prefers ProblemDetail `detail`, else maps status to a message.
- `clearAuth()`, `clearError()`, `setSessionTimeout()`.

View:
- `handleLogin` (`LoginView.vue:141`): clears error, validates (email format and a non-empty password, no minimum length, `:112-139`), calls `authStore.login`, toasts, then `router.push` to `?redirect` or `/` (`:175-189`). Failure toasts `authStore.authError` (`:190-198`).

## Interceptor (`api.js`, auth parts)
- Request: copies `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` on post/put/patch/delete (`:30-52`). The backend requires it on every write, login included, and sets the cookie on its first response; see CSRF in [../authentication.md](../authentication.md).
- Response 401 (`:105`): skips URLs containing `/login` or `/refresh` (`:109`); otherwise calls `refreshToken`, bumps activity, retries the request once (`_retry`, `:106`, `:126`).
- Refresh failure (`:127-170`): `clearAuth`, removes `user`/`auth_timestamp` from local/session storage, toasts "Session Expired" and hard-redirects to `/login?session=expired`, both only if the user was signed in (`:166`).
- Other 401 shows an "Unauthorized" toast (`:174-195`).

## Collaborators
- `frontend/src/services/api.js` (above), `frontend/src/stores/appStore` (`addNotification`), `vue-router`.
- Router guard: `router/index.js:81-83` logs out and redirects to `/login?session=expired` when `isSessionExpired()`; `:97-99` sends a signed-in user from `/login` to `homePath`.

## Errors
Shown in the form alert (`LoginView.vue:15`) and a toast.
- Client: "Email is required", "Please enter a valid email address", "Password is required" (`:112-130`).
- Server: ProblemDetail `detail` shown verbatim (`authStore.js:165`). Backend login failures are 400, so the status-based fallbacks (`:219-236`) apply only when there is no `detail` (e.g. network error -> `error.message`).

## Side effects
- `sessionStorage.auth_timestamp` set on login and refresh (`api.js:414`, `:458`); `user`/`auth_timestamp` removed on logout and refresh failure (`:447-449`, `:464-466`).
- Document-level activity listeners added by `initialize()` (never removed).
- 30 s `setInterval` while signed in.

## Gotchas
- Sessions renew: an expired access cookie gets a 401, the interceptor refreshes once and retries (`api.js:105-126`), so the 1 h client idle timeout is the limit. The "Access Denied" toast appears only for a real 403 (signed in, role too low), not for an expired session.
- The "Session Expired" toast on `LoginView` fires only after a successful sign-in (`LoginView.vue:179-186`), not on arrival at `/login?session=expired`.
- The error alert stays until the next submit (nothing clears it on typing). The unbound `clearErrorOnInput`, the dead `authStore.register` (it posted to a path that does not exist), `refreshToken` and `forceLogout` were removed in `chore(ui): remove dead frontend code`.
- A locked account shows the same generic message as any failed sign-in (the lock ends after 15 minutes); too many failures give a 429 whose message the form shows.
