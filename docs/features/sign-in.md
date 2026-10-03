# Sign-in and sessions

How staff, volunteers and members sign in, stay signed in, and get sent to the right page. Self-registration is disabled; accounts are created in SQL (`src/main/java/io/github/membertracker/infrastructure/AuthController.java:85-90`).

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| See the landing page | none (signed out only) | `/` (`frontend/src/router/index.js:6-11`) |
| Sign in | none (signed out only) | `/login` (`router/index.js:42-47`), `POST /api/auth/login` |
| Stay signed in | any signed-in user | cookies, `POST /api/auth/refresh` |
| Sign out | any signed-in user | Sign Out menu item (`frontend/src/App.vue:41`), `POST /api/auth/logout` |
| Open `/dashboard`, `/members`, `/payments`, `/communications` | VOLUNTEER+ | `router/index.js:12-35` |
| Open `/profile` | MEMBER+ (any signed-in user) | `router/index.js:36-41` |

The auth endpoints have no `@PreAuthorize`; `/api/auth/**` is public (`infrastructure/config/SecurityConfig.java:125-126`). CSRF protection still applies: the browser first gets the `XSRF-TOKEN` cookie from any GET (the app's first call is `/api/users/me`), and every POST, including login, refresh and logout, carries it back as `X-XSRF-TOKEN`; without it the answer is 403 "Invalid or missing CSRF token".

## How it works
### Sign in
1. A signed-out visitor opens `/login`, enters email and password (`frontend/src/views/LoginView.vue:149`).
2. The form checks email format and a password of 6+ characters before sending anything (`LoginView.vue:118-146`). Failure: inline field errors and a "Validation Error" toast.
3. The store lowercases the email and posts to `/api/auth/login` (`frontend/src/stores/authStore.js:62-80`).
4. The backend looks up the user, clears an expired lock, rejects a locked account (without checking the password), checks the password, then checks enabled and credentials not expired (`src/main/java/io/github/membertracker/usecase/AuthenticateUserUseCase.java:19-44`).
5. On success it sets the `sid` and `sid_refresh` cookies and returns `{email, role}` (`AuthController.java:56-78`). The user sees a "Welcome back!" toast and is sent to the `?redirect=` page, or `/` (`LoginView.vue:175-197`).
6. `/` is guest-only, so the guard forwards a signed-in user to their home page: `/dashboard` for VOLUNTEER+, `/profile` for MEMBER (`router/index.js:91-96`, `authStore.js:43`).
7. On failure the server message is shown in the form alert and a "Login Failed" toast (`LoginView.vue:198-206`, `authStore.js:210-217`). Login errors come back as HTTP 400 (`AuthController.java:80-81`), with one message, "Invalid email or password. After several failed attempts an account is locked for 15 minutes.", for an unknown email, a wrong password and a locked account. After 10 failures for one email or 30 from one IP in 10 minutes the next attempt is a 429 with `Retry-After` (`LoginAttemptLimiter`).

### Stay signed in
1. The access cookie `sid` lasts 30 minutes, the refresh cookie `sid_refresh` 30 days (`infrastructure/config/AuthProperties.java:11-12`). Cookie and CSRF settings: [../authentication.md](../authentication.md).
2. When an API call returns 401, the browser calls `/api/auth/refresh` once and retries the original request (`frontend/src/services/api.js:105-126`).
3. The refresh cookie is scoped to `/api/auth`, so the browser sends it to `/api/auth/refresh` (`utils/CookieUtils.java:33`, `AuthController.java:97-100`). Only a refresh token is accepted there; an access token gets 400.
4. If the refresh fails, the user sees a "Session Expired" error toast and is hard-redirected to `/login?session=expired` (`api.js:147-152`, `api.js:166-168`).
5. Client-side idle timeout: after 1 hour without mouse, key, scroll or touch activity, or any API request, the store logs the user out (`authStore.js:18`, `:246-258`, `:283-285`; request bump at `api.js:56-62`). The check runs every 30 seconds, and again on each route change (`router/index.js:75-79`).
6. Idle-timeout fix: `isSessionExpired()` and `getTimeUntilExpiry()` are plain functions that read the clock on every call, not cached computeds (`authStore.js:45-53`). Because sessions renew through the refresh cookie while the user is active, this 1-hour idle timeout is the intended limit.
7. On page reload, the router guard asks `GET /users/me` once to restore the session (`router/index.js:65-70`, `authStore.js:159-181`).

### Sign out
1. The user picks Sign Out in the navbar (`App.vue:140-152`).
2. The store stops the idle timer, posts `/api/auth/logout`, then clears local state even if the call fails (`authStore.js:136-157`).
3. The server only clears both cookies (`AuthController.java:128-136`). The user lands on `/login`.

### Being redirected
1. Signed-out user on a protected route goes to `/login?redirect=<path>` (omitted for `/` and `/dashboard`) (`router/index.js:82-88`).
2. Signed-in user on `/` or `/login` goes to their home page (`router/index.js:91-96`).
3. Signed-in user below VOLUNTEER on a staff route sees an "Access denied" warning toast ("You don't have access to that page.", 5 s) and goes to their home page, `/profile` for a MEMBER, with no query parameter (`router/index.js:103-112`).
4. Unknown URLs redirect to `/` (`router/index.js:48-51`).
5. An idle-expired user is logged out and sent to `/login?session=expired` (`router/index.js:75-79`).

### A session for an account that no longer works
1. If a user is deleted, disabled or locked while their `sid` cookie is still valid, the next request is treated as signed out: protected endpoints answer 401 (the browser then tries `/api/auth/refresh`, which also fails, and sends the user to `/login?session=expired`).
2. The stale cookie does not block sign-in: `/api/auth/login`, `/refresh` and `/logout` work normally with it (`src/main/java/io/github/membertracker/infrastructure/filter/JwtAuthenticationFilter.java:62-66`). Before this fix those calls returned 500 for up to 30 minutes.

### Account lockout
1. Each wrong password for an existing email adds one to the failed-attempt counter with a single-row SQL update (`UserRepository.recordFailedLogin`); an unknown email has no counter to increase. The user is never saved as a whole on sign-in.
2. The fifth failure locks the account for 15 minutes (`User.MAX_FAILED_LOGIN_ATTEMPTS`, `User.LOCK_DURATION`, column `users.locked_until`).
3. The lock is checked before the password. While locked, even the right password gets the same generic 400 as any failure, and the password is not checked, so the lock reveals nothing.
4. When `locked_until` has passed, the next attempt clears the lock and counter and carries on normally; a successful sign-in resets the counter. A lock with no `locked_until` stays permanent until the database is edited (see [../authentication.md](../authentication.md)).
5. A wrong current password on a password change counts toward the same lock.

### What a signed-out visitor sees
1. `/` shows the landing page with a Sign In button (`frontend/src/views/LandingView.vue:11-14`). There is no Register button: registration is disabled and `/register` is not a route.

## Rules
- Roles rank MEMBER < VOLUNTEER < STAFF < ADMIN on the client (`authStore.js:21-30`); route `requiresRole` is a minimum (`router/index.js:103`).
- Five failed logins lock the account for 15 minutes (`User.java`, `AuthenticateUserUseCase.java`).
- Disabled and credential-expired accounts are rejected after the password check (`AuthenticateUserUseCase.java`).
- Registration always returns 403 "Registration is disabled. Please contact administrator for access." (`AuthController.java:89`).
- Logout does not revoke tokens; they stay valid until they expire (`AuthController.java:128-136`).

## Known issues
- Disabled and expired-credential messages are only returned after the correct password, so they confirm the password (the lock message no longer does: a locked account answers like any failure).
- The "Session Expired" toast on `LoginView` fires only after the next successful sign-in, not on arrival at `/login?session=expired` (`LoginView.vue:187-194`).
- The form error alert stays until the next submit: `clearErrorOnInput` is defined but not bound (`LoginView.vue:210-214`).
- No password reset, no admin unlock for permanent locks, no admin screen for accounts (see [../authentication.md](../authentication.md)).

## Related
- [auth-controller.md](auth-controller.md), [login-view.md](login-view.md), [user-controller.md](user-controller.md), [profile-view.md](profile-view.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
