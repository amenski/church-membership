# Sign-in and sessions

How staff, volunteers and members sign in, stay signed in, and get sent to the right page. Self-registration is disabled; accounts are created in SQL (`src/main/java/io/github/membertracker/infrastructure/AuthController.java:85-90`).

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| See the landing page | none (signed out only) | `/` (`frontend/src/router/index.js:5-10`) |
| Sign in | none (signed out only) | `/login` (`router/index.js:41-46`), `POST /api/auth/login` |
| Stay signed in | any signed-in user | cookies, `POST /api/auth/refresh` |
| Sign out | any signed-in user | Sign Out menu item (`frontend/src/App.vue:41`), `POST /api/auth/logout` |
| Open `/dashboard`, `/members`, `/payments`, `/communications` | VOLUNTEER+ | `router/index.js:11-34` |
| Open `/profile` | MEMBER+ (any signed-in user) | `router/index.js:35-40` |

The auth endpoints have no `@PreAuthorize`; `/api/auth/**` is public (`infrastructure/config/SecurityConfig.java:81-82`).

## How it works
### Sign in
1. A signed-out visitor opens `/login`, enters email and password (`frontend/src/views/LoginView.vue:149`).
2. The form checks email format and a password of 6+ characters before sending anything (`LoginView.vue:118-146`). Failure: inline field errors and a "Validation Error" toast.
3. The store lowercases the email and posts to `/api/auth/login` (`frontend/src/stores/authStore.js:62-80`).
4. The backend looks up the user, checks the password, then checks enabled, not locked and credentials not expired (`src/main/java/io/github/membertracker/usecase/AuthenticateUserUseCase.java:19-44`).
5. On success it sets the `sid` and `sid_refresh` cookies and returns `{email, role}` (`AuthController.java:56-78`). The user sees a "Welcome back!" toast and is sent to the `?redirect=` page, or `/` (`LoginView.vue:175-197`).
6. `/` is guest-only, so the guard forwards a signed-in user to their home page: `/dashboard` for VOLUNTEER+, `/profile` for MEMBER (`router/index.js:90-95`, `authStore.js:43`).
7. On failure the server message is shown in the form alert and a "Login Failed" toast (`LoginView.vue:198-206`, `authStore.js:210-217`). Login errors come back as HTTP 400 (`AuthController.java:80-81`), with one message, "Invalid email or password", for an unknown email and a wrong password.

### Stay signed in
1. The access cookie `sid` lasts 30 minutes, the refresh cookie `sid_refresh` 30 days (`infrastructure/config/AuthProperties.java:11-12`). Cookie and CSRF settings: [../authentication.md](../authentication.md).
2. When an API call returns 401, the browser calls `/api/auth/refresh` once and retries the original request (`frontend/src/services/api.js:105-126`).
3. The refresh cookie is scoped to `/api/auth`, so the browser sends it to `/api/auth/refresh` (`utils/CookieUtils.java:33`, `AuthController.java:97-100`). Only a refresh token is accepted there; an access token gets 400.
4. If the refresh fails, the user sees a "Session Expired" error toast and is hard-redirected to `/login?session=expired` (`api.js:147-152`, `api.js:166-168`).
5. Client-side idle timeout: after 1 hour without mouse, key, scroll or touch activity, or any API request, the store logs the user out (`authStore.js:18`, `:246-258`, `:283-285`; request bump at `api.js:56-62`). The check runs every 30 seconds, and again on each route change (`router/index.js:74-78`).
6. Idle-timeout fix: `isSessionExpired()` and `getTimeUntilExpiry()` are plain functions that read the clock on every call, not cached computeds (`authStore.js:45-53`). Because sessions renew through the refresh cookie while the user is active, this 1-hour idle timeout is the intended limit (but see the 403 issue under Known issues).
7. On page reload, the router guard asks `GET /users/me` once to restore the session (`router/index.js:64-69`, `authStore.js:159-181`).

### Sign out
1. The user picks Sign Out in the navbar (`App.vue:140-152`).
2. The store stops the idle timer, posts `/api/auth/logout`, then clears local state even if the call fails (`authStore.js:136-157`).
3. The server only clears both cookies (`AuthController.java:128-136`). The user lands on `/login`.

### Being redirected
1. Signed-out user on a protected route goes to `/login?redirect=<path>` (omitted for `/` and `/dashboard`) (`router/index.js:81-87`).
2. Signed-in user on `/` or `/login` goes to their home page (`router/index.js:90-95`).
3. Signed-in user below VOLUNTEER on a staff route goes to `/profile?error=access_denied` (`router/index.js:102-105`).
4. Unknown URLs redirect to `/` (`router/index.js:47-50`).
5. An idle-expired user is logged out and sent to `/login?session=expired` (`router/index.js:74-78`).

### Account lockout
1. Each wrong password for an existing email adds one to the failed-attempt counter and saves it (`AuthenticateUserUseCase.java:28-32`); an unknown email has no counter to increase.
2. The fifth failure locks the account (`src/main/java/io/github/membertracker/domain/model/User.java:214-220`).
3. A locked user gets "Account for user '...' is locked" even with the right password (`AuthenticateUserUseCase.java:38-40`, `domain/exception/UserDomainException.java:94-98`).
4. A successful login resets the counter (`AuthenticateUserUseCase.java:46-47`). Nothing unlocks a locked account automatically; an admin must set `account_non_locked` in the database (see [../authentication.md](../authentication.md)).

### What a signed-out visitor sees
1. `/` shows the landing page with Sign In and Register buttons (`frontend/src/views/LandingView.vue:11-18`).
2. Register goes to `/register`, which is not a route, so it falls through to `/` and nothing visible happens (`router/index.js:47-50`).

## Rules
- Roles rank MEMBER < VOLUNTEER < STAFF < ADMIN on the client (`authStore.js:21-30`); route `requiresRole` is a minimum (`router/index.js:102`).
- Five failed logins lock the account (`User.java:218-219`).
- Disabled and credential-expired accounts are rejected after the password check (`AuthenticateUserUseCase.java:34-44`).
- Registration always returns 403 "Registration is disabled. Please contact administrator for access." (`AuthController.java:89`).
- Logout does not revoke tokens; they stay valid until they expire (`AuthController.java:128-136`).

## Known issues
- Automatic renewal does not trigger yet: the interceptor refreshes only on 401, but a missing or expired access cookie gets 403 (no authentication entry point), so the session ends when the 30-minute access cookie expires. See [auth-controller.md](auth-controller.md) gotchas, [login-view.md](login-view.md) gotchas.
- A locked account's message only appears after the correct password (disabled and expired-credential messages likewise), which confirms the password (`AuthenticateUserUseCase.java:34-44`).
- The lockout counter only counts failures for existing accounts (by design: nothing to count for an unknown email).
- The "Session Expired" toast on `LoginView` fires only after the next successful sign-in, not on arrival at `/login?session=expired` (`LoginView.vue:187-194`).
- The form error alert stays until the next submit: `clearErrorOnInput` is defined but not bound (`LoginView.vue:210-214`).
- The landing page Register button leads nowhere (`LandingView.vue:15`).
- No password reset, no unlock flow, no admin screen for accounts (see [../authentication.md](../authentication.md)).

## Related
- [auth-controller.md](auth-controller.md), [login-view.md](login-view.md), [user-controller.md](user-controller.md), [profile-view.md](profile-view.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
