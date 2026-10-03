# ProfileView

`frontend/src/views/ProfileView.vue`

"My Profile" page: view and edit own name, phone, bio, and change password. Route `/profile`, any signed-in user (`requiresAuth`, `frontend/src/router/index.js:37-40`); MEMBER's home page. Guards: see [../authentication.md](../authentication.md).

## State
Local `ref`s; no store of its own. Reads `useAuthStore().user` only as the initial `user` value (`ProfileView.vue:298`).

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

## Actions
- `loadProfile` (on mount, `:363`) -> `apiService.getCurrentUser()`; fills `user` and `form`; on failure `error = 'Failed to load profile'`.
- `startEditing` / `cancelEditing` (`:383`, `:389`) -> toggle `editing`; cancel resets `form` from `user`.
- `handleSubmit` (`:401`) -> `validateForm`, then `apiService.updateProfile(form)`; on success sets `user` and `authStore.user`, leaves edit mode, shows success. Failure: `error = 'Failed to update profile'`.
- `handlePasswordChange` (`:480`) -> `validatePasswordForm`, then `apiService.changePassword({currentPassword, newPassword})`; on success clears the form, hides the dialog with `Modal.getOrCreateInstance(...).hide()` and adds a success toast through the app store. Failure: `passwordServerError` = the first field message, else the server's `detail` (the API interceptor puts it in `error.message`); the dialog stays open and the page is untouched.
- The Change Password button opens the dialog through Bootstrap's data API (`data-bs-toggle="modal" data-bs-target="#changePasswordModal"`, `:195-196`). `resetPasswordForm` (`:465`) also runs on the dialog's `hidden.bs.modal` event (`:513`, removed on unmount `:517`).

### Client validation
| Field | Rule |
|-------|------|
| first/last name | required (kept from the original form; the server alone would only ignore a blank name); no minimum; `maxlength="50"` on the inputs (`:72`, `:93`, checks `:336-346`) |
| phone | optional; blank is valid; otherwise `isValidPhone` from `frontend/src/utils/phoneRules.js`, which uses the server's exact pattern `^\+?[0-9\s\-()]{10,}$` (`:349-352`) |
| bio | max 500 (`:355`) |
| new password | required; `validateNewPassword` from `frontend/src/utils/passwordRules.js` (8-72 UTF-8 bytes, upper, lower, digit, special = not letter, digit or whitespace; same rule as the server) (`:443-451`); confirm must match |

## Collaborators
- `apiService.getCurrentUser` `frontend/src/services/api.js:466` -> `GET /users/me`; also calls `authStore.updateActivity()`.
- `apiService.updateProfile` `api.js:480` -> `PUT /users/me/profile`.
- `apiService.changePassword` `api.js:485` -> `PUT /users/me/password`.
- `validateNewPassword` `frontend/src/utils/passwordRules.js`; `useAppStore().addNotification` for the success toast (`:496`).
- Backend: [user-controller.md](user-controller.md).
- `useAuthStore` (`@/stores/index.js`): `user` is written on save (`:414`).

## Errors
- Load and profile-save failures are still one fixed string each (see Actions); details are only in `console.error`.
- Password-change failures show the server's message in the dialog: "Current password is incorrect" or the password rule text.
- 401/403 handling is in the `api.js` response interceptor, not here; see [../authentication.md](../authentication.md).

## Side effects
- `authStore.user` is replaced with the update response (`:414`).
- A success toast is added to the app store after a password change.
- `getCurrentUser` bumps the store's activity timestamp (idle-logout timer).
- 5 s `setTimeout` clears `successMessage` after a profile save (`:418`).

## Gotchas
- The Account Information card used to show a hard-coded "Active" status, an always-N/A "Member Since" and an "Export Data" button with no handler; all three were removed. Showing a real status or join date would need `enabled`/`createdAt` in the API first (`UserResponseDto` has no `createdAt`).
- A failed profile save sets the page-level `error`, which hides the whole profile and offers no retry; reload to recover. Password failures stay in the dialog.
- A MEMBER sent here after trying a staff URL sees the router guard's "Access denied" warning toast (`router/index.js:103-112`); the view itself reads nothing.
