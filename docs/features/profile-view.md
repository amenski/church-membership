# ProfileView

`frontend/src/views/ProfileView.vue`

"My Profile" page: view and edit own name, phone, bio, and change password. Route `/profile`, any signed-in user (`requiresAuth`, `frontend/src/router/index.js:36-39`); MEMBER's home page. Guards: see [../authentication.md](../authentication.md).

## State
Local `ref`s; no store of its own. Reads `useAuthStore().user` only as the initial `user` value (`ProfileView.vue:309-310`).

| Ref | Meaning |
|-----|---------|
| `user` | last server copy of the profile; drives role badge, email, cancel-reset |
| `form` / `formErrors` | editable `firstName`, `lastName`, `phone`, `bio` and per-field messages |
| `editing` | fields are disabled unless true |
| `loading` | initial fetch in progress (starts `true`); spinner replaces the content |
| `saving` | profile save in progress |
| `error` | single page-level message; when set it **replaces the whole page content** (`v-else-if`, `:24`) |
| `successMessage` | green alert, auto-cleared after 5 s |
| `passwordForm` / `passwordErrors` / `changingPassword` | change-password modal state |
| `formatMemberSince` | computed from `user.createdAt`, `'N/A'` if missing |

## Actions
- `loadProfile` (on mount, `:402`) -> `apiService.getCurrentUser()`; fills `user` and `form`; on failure `error = 'Failed to load profile'`.
- `startEditing` / `cancelEditing` (`:422`, `:428`) -> toggle `editing`; cancel resets `form` from `user`.
- `handleSubmit` (`:440`) -> `validateForm`, then `apiService.updateProfile(form)`; on success sets `user` and `authStore.user`, leaves edit mode, shows success. Failure: `error = 'Failed to update profile'`.
- `handlePasswordChange` (`:515`) -> `validatePasswordForm`, then `apiService.changePassword({currentPassword, newPassword})`; on success clears the form and sets success. Failure: `error = 'Failed to change password. Please check your current password.'`.

### Client validation
| Field | Rule |
|-------|------|
| first/last name | required, min 2 (`:364-379`) |
| phone | optional; after stripping spaces, `-`, `(`, `)` must match `^[\+]?[1-9][\d]{0,15}$` (`:397-399`) |
| bio | max 500 (`:388`) |
| new password | required, min 8 (`:483`); confirm must match (`:492`) |

## Collaborators
- `apiService.getCurrentUser` `frontend/src/services/api.js:466` -> `GET /users/me`; also calls `authStore.updateActivity()`.
- `apiService.updateProfile` `api.js:480` -> `PUT /users/me/profile`.
- `apiService.changePassword` `api.js:485` -> `PUT /users/me/password`.
- Backend: [user-controller.md](user-controller.md).
- `useAuthStore` (`@/stores/index.js`): `user` is written on save (`:453`).

## Errors
- The view discards the server message and status; every failure maps to one fixed string (see Actions). Details are only in `console.error`.
- 401/403 handling is in the `api.js` response interceptor, not here; see [../authentication.md](../authentication.md).

## Side effects
- `authStore.user` is replaced with the update response (`:453`).
- `getCurrentUser` bumps the store's activity timestamp (idle-logout timer).
- 5 s `setTimeout` clears `successMessage` (`:457`, `:535`).

## Gotchas
- "Member Since" always shows `N/A`: the backend DTO has no `createdAt` (`infrastructure/dto/UserResponseDto.java:4-11`) and the view reads `user.createdAt` (`ProfileView.vue:345`).
- "Account Status: Active / Verified account" is hard-coded (`:193-196`), not read from `user.enabled`.
- "Export Data" button has no click handler (`:216`).
- A failed password change or save sets the page-level `error`, which hides the whole profile and offers no retry; reload to recover.
- The password hint and client check only say "at least 8 characters" (`:264`, `:483`); the server also requires upper, lower, digit and one of `@$!%*?&` and rejects other characters (`domain/model/User.java:38`). A weak password passes the client and returns the generic message, which blames the current password.
- The "Change Password" button only sets `showChangePasswordModal = true` (`ProfileView.vue:212`). Nothing reads that ref and nothing calls Bootstrap's `Modal.show` (the ref `changePasswordModal` is not returned from `setup`, `:550-568`), so the modal never opens and the password flow is unreachable from this page.
- Client phone regex rejects numbers starting with `0`, which the server pattern accepts (`UpdateUserProfileRequest.java:14`).
- First/last name must be >= 2 chars client-side; the server has no minimum.
