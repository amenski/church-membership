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
| `passwordForm` / `passwordErrors` / `changingPassword` | change-password dialog state |
| `passwordServerError` | the server's reason for a failed password change, shown as a red alert inside the dialog |
| `formatMemberSince` | computed from `user.createdAt`, `'N/A'` if missing |

## Actions
- `loadProfile` (on mount, `:402`) -> `apiService.getCurrentUser()`; fills `user` and `form`; on failure `error = 'Failed to load profile'`.
- `startEditing` / `cancelEditing` (`:422`, `:428`) -> toggle `editing`; cancel resets `form` from `user`.
- `handleSubmit` (`:440`) -> `validateForm`, then `apiService.updateProfile(form)`; on success sets `user` and `authStore.user`, leaves edit mode, shows success. Failure: `error = 'Failed to update profile'`.
- `handlePasswordChange` (`:526`) -> `validatePasswordForm`, then `apiService.changePassword({currentPassword, newPassword})`; on success clears the form, hides the dialog with `Modal.getOrCreateInstance(...).hide()` and adds a success toast through the app store. Failure: `passwordServerError` = the first field message, else the server's `detail` (the API interceptor puts it in `error.message`); the dialog stays open and the page is untouched.
- The Change Password button opens the dialog through Bootstrap's data API (`data-bs-toggle="modal" data-bs-target="#changePasswordModal"`, `:212-213`). `resetPasswordForm` (`:511`) also runs on the dialog's `hidden.bs.modal` event (`:559`, removed on unmount `:563`).

### Client validation
| Field | Rule |
|-------|------|
| first/last name | required, min 2 (`:364-379`) |
| phone | optional; after stripping spaces, `-`, `(`, `)` must match `^[\+]?[1-9][\d]{0,15}$` (`:397-399`) |
| bio | max 500 (`:388`) |
| new password | required; `validateNewPassword` from `frontend/src/utils/passwordRules.js` (8-72 UTF-8 bytes, upper, lower, digit, special = not letter, digit or whitespace; same rule as the server) (`:487-495`); confirm must match |

## Collaborators
- `apiService.getCurrentUser` `frontend/src/services/api.js:466` -> `GET /users/me`; also calls `authStore.updateActivity()`.
- `apiService.updateProfile` `api.js:480` -> `PUT /users/me/profile`.
- `apiService.changePassword` `api.js:485` -> `PUT /users/me/password`.
- `validateNewPassword` `frontend/src/utils/passwordRules.js`; `useAppStore().addNotification` for the success toast (`:542`).
- Backend: [user-controller.md](user-controller.md).
- `useAuthStore` (`@/stores/index.js`): `user` is written on save (`:453`).

## Errors
- Load and profile-save failures are still one fixed string each (see Actions); details are only in `console.error`.
- Password-change failures show the server's message in the dialog: "Current password is incorrect" or the password rule text.
- 401/403 handling is in the `api.js` response interceptor, not here; see [../authentication.md](../authentication.md).

## Side effects
- `authStore.user` is replaced with the update response (`:453`).
- A success toast is added to the app store after a password change.
- `getCurrentUser` bumps the store's activity timestamp (idle-logout timer).
- 5 s `setTimeout` clears `successMessage` after a profile save (`:457`).

## Gotchas
- "Member Since" always shows `N/A`: the backend DTO has no `createdAt` (`infrastructure/dto/UserResponseDto.java:4-11`) and the view reads `user.createdAt` (`ProfileView.vue:345`).
- "Account Status: Active / Verified account" is hard-coded (`:193-196`), not read from `user.enabled`.
- "Export Data" button has no click handler (`:216`).
- A failed profile save sets the page-level `error`, which hides the whole profile and offers no retry; reload to recover. Password failures stay in the dialog.
- Client phone regex rejects numbers starting with `0`, which the server pattern accepts (`UpdateUserProfileRequest.java:14`).
- First/last name must be >= 2 chars client-side; the server has no minimum.
- Saving with an empty phone sends `""` (the form starts at `''` and `:411` fills `data.phone || ''`; `:382` skips the check when empty), but the server's `@Pattern` only skips `null`, and `""` does not match `{10,}`, so the save fails with 400 (`UpdateUserProfileRequest.java:13-14`). Found by reading the regex and the form code, not run. The user sees "Failed to update profile".
- Short numbers such as `5551234` pass the client regex (`:398`) but fail the server, which needs at least 10 characters (`UpdateUserProfileRequest.java:14`).
- Names over 50 characters pass the form (no `maxlength` on the inputs, no cap in `validateForm`, `:364-379`) but fail the server (`@Size(max = 50)`, `UpdateUserProfileRequest.java:8`, `:11`).
- `?error=access_denied` (added by the router guard, `router/index.js:103`) is never read by this view, so a MEMBER redirected here sees no message.
