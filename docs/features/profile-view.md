# ProfileView

`frontend/src/views/ProfileView.vue`

"Profile" page ("Your details and password."): view and edit own name, phone, bio, and change password. Route `/profile`, any signed-in user (`requiresAuth`, `frontend/src/router/index.js`); a MEMBER's home is My dues, which links here. Guards: see [../authentication.md](../authentication.md). Built on Tailwind and the shared components (`PageHead`, `SectionTitle`, `BaseInput`, `BaseButton`, `BaseModal`, `AlertBanner`); no Bootstrap classes, JavaScript or scoped CSS. One column, max 640px, left aligned, two paper cards. It follows the shell's `data-density` (comfortable for a MEMBER: 48px primary button, full width below 640px; dense for staff).

## State
Local `ref`s; no store of its own. Reads `useAuthStore().user` only as the initial `user` value.

| Ref | Meaning |
|-----|---------|
| `user` | last server copy of the profile; drives email and role |
| `form` / `formErrors` | editable `firstName`, `lastName`, `phone`, `bio` and per-field messages. Fields are always editable (there is no edit mode) |
| `loading` | initial fetch in progress (starts `true`) |
| `error` | load failure message; shows a banner with "Try again" in place of the cards |
| `saving` | profile save in progress |
| `saveError` | banner inside the details card for a failed save (the page stays visible) |
| `successMessage` | green banner "Profile updated successfully!", auto-cleared after 5 s |
| `passwordOpen` | the Change password dialog is open |
| `passwordForm` / `passwordErrors` / `changingPassword` | change-password dialog state |
| `passwordServerError` | the server's reason for a failed password change, shown as a banner inside the dialog |

## Actions
- `loadProfile` (on mount and "Try again") -> `apiService.getCurrentUser()`; fills `user` and `form`; on failure `error = 'Failed to load profile'`.
- `handleSubmit` ("Save changes") -> `validateForm`, then `apiService.updateProfile(form)`; on success sets `user` and `authStore.user` and shows the success banner. Failure: server field errors (`err.fieldErrors`, `{field, message}`) go under their field; otherwise `saveError = 'Failed to update profile'` in the card. The page content is not replaced.
- "Change password" opens a `BaseModal` (fields Current password, New password, Confirm new password; the rule text from `PASSWORD_RULE_MESSAGE` as the hint under New password). `handlePasswordChange` -> `validatePasswordForm`, then `apiService.changePassword({currentPassword, newPassword})`; on success clears the form, closes the dialog and adds a success toast "Password changed" through the app store. Failure: `passwordServerError` = the first field message, else the server's `detail` (the API interceptor puts it in `error.message`); the dialog stays open. A watcher on `passwordOpen` resets the form whenever the dialog closes (Cancel, Escape, backdrop or success).

### Client validation
| Field | Rule |
|-------|------|
| first/last name | required (kept from the original form; the server alone would only ignore a blank name); no minimum; `maxlength="50"` on the inputs |
| phone | optional; blank is valid; otherwise `isValidPhone` from `frontend/src/utils/phoneRules.js`, which uses the server's exact pattern `^\+?[0-9\s\-()]{10,}$` |
| bio | max 500; a live counter ("123 of 500") under the box turns red past 500; no `maxlength`, the validation message "Bio must be less than 500 characters" shows on save |
| new password | required; `validateNewPassword` from `frontend/src/utils/passwordRules.js` (8-72 UTF-8 bytes, upper, lower, digit, special = not letter, digit or whitespace; same rule as the server); confirm must match |

## Collaborators
- `apiService.getCurrentUser` `frontend/src/services/api.js` -> `GET /users/me`; also calls `authStore.updateActivity()`.
- `apiService.updateProfile` -> `PUT /users/me/profile`.
- `apiService.changePassword` -> `PUT /users/me/password`.
- `validateNewPassword`, `PASSWORD_RULE_MESSAGE` `frontend/src/utils/passwordRules.js`; `useAppStore().addNotification` for the password toast.
- Backend: [user-controller.md](user-controller.md).
- `useAuthStore` (`@/stores/index.js`): `user` is written on save.

## Errors
- Load failure: banner "Failed to load profile. Check your connection and try again." with a "Try again" button.
- Profile-save failures: field errors under the field, otherwise "Failed to update profile" in the card; details in `console.error`.
- Password-change failures show the server's message in the dialog: "Current password is incorrect" or the password rule text.
- 401/403 handling is in the `api.js` response interceptor, not here; see [../authentication.md](../authentication.md).

## Side effects
- `authStore.user` is replaced with the update response.
- A success toast is added to the app store after a password change.
- `getCurrentUser` bumps the store's activity timestamp (idle-logout timer).
- 5 s `setTimeout` clears `successMessage` after a profile save.

## Gotchas
- Email and role are shown as plain text (no disabled input); the role word is the stored role in sentence case ("Staff").
- The old Account Information card showed a hard-coded "Active" status, an always-N/A "Member Since" and an "Export Data" button with no handler; all were removed. Showing a real status or join date would need `enabled`/`createdAt` in the API first (`UserResponseDto` has no `createdAt`).
- The Change password dialog is teleported to `body`, outside the shell's `data-density` scope, so its sizes are fixed (18px inputs), not density variables.
- A MEMBER who tries a staff URL is sent to My dues, not here; the router guard's "Access denied" toast is raised before this view loads.
