# Profile and password

Every signed-in user views and edits their own name, phone and bio, and changes their own password. A MEMBER has two screens: this one and [My dues](my-dues.md).

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| View own profile | MEMBER | `/profile`, `GET /api/users/me` |
| Edit own name, phone, bio | MEMBER | `/profile` ("Your details" card, "Save changes"), `PUT /api/users/me/profile` |
| Change the UI language | any role | the "Language" card on `/profile`, the rail or top bar, or the sign-in page; saved with `PUT /api/users/me/language` ([i18n](i18n.md)) |
| Change own password | MEMBER | `/profile` ("Change password" button and dialog), `PUT /api/users/me/password` |
| Edit email, role or another user | nobody | no UI or API |

- No `@PreAuthorize` on these endpoints; any authenticated user passes (`.anyRequest().authenticated()`, `src/main/java/io/github/membertracker/infrastructure/config/SecurityConfig.java`).
- The user always comes from the security context, never from an id in the request (`src/main/java/io/github/membertracker/infrastructure/UserController.java`).
- Route `/profile` only needs sign-in (`frontend/src/router/index.js`).

## How it works
### View own profile
1. Click your name in the account block of the rail (`frontend/src/App.vue`), or choose Profile on More on a phone; every role has it.
2. The page loads `GET /users/me` and fills the form (`frontend/src/views/ProfileView.vue`). While it loads the page says "Loading profile...".
3. The page head is "Profile" ("Your details and password."). Two paper cards follow: "Your details" and "Password". "Your details" starts with Email and Role as plain text and the line "Your email cannot be changed here." (`ProfileView.vue`). The role is shown as a word in sentence case ("Staff").
4. The fields are always editable: there is no Edit or Cancel toggle. A load failure replaces the cards with a banner "Failed to load profile. Check your connection and try again." and a "Try again" button (`ProfileView.vue`).
5. A MEMBER sees the page at the comfortable density (48px "Save changes", full width on a phone); staff see it dense (`frontend/src/App.vue`).

### Edit own profile
1. Change first name, last name, phone or bio in "Your details". The name inputs stop at 50 characters (`maxlength`); the bio has a live counter ("123 of 500") that turns red past 500 (`ProfileView.vue`).
2. "Save changes" runs the client checks first (`ProfileView.vue`), then sends `PUT /users/me/profile` (`ProfileView.vue`, `frontend/src/services/api.js`). The server turns a blank phone into null, validates the DTO (`src/main/java/io/github/membertracker/infrastructure/dto/UpdateUserProfileRequest.java`, its setter), then `User.updateProfile` trims and stores the values (`src/main/java/io/github/membertracker/domain/model/User.java`).
3. Success: a green banner "Profile updated successfully!" for 5 seconds, and the signed-in user in the store is replaced (`ProfileView.vue`). The name in the rail updates.
4. Failure: a server field error (`err.fieldErrors`) shows under its field; anything else shows "Failed to update profile" in a banner inside the card. The form stays on screen and keeps what was typed (`ProfileView.vue`).

### Change password
1. The "Change password" button opens a dialog (`BaseModal`, `ProfileView.vue`) with "Current password", "New password" (the password rule as its hint) and "Confirm new password"; buttons "Cancel" and "Change password".
2. Client checks on submit: current is present, new follows the password rule (`frontend/src/utils/passwordRules.js`, same rule as the server), confirm matches (`ProfileView.vue`).
3. `PUT /users/me/password`. The server checks the current password (`src/main/java/io/github/membertracker/usecase/ChangePasswordUseCase.java`), then the strength rule, then stores the new hash.
4. Success: fresh session cookies plus `{"message": "Password changed successfully"}` (`src/main/java/io/github/membertracker/infrastructure/UserController.java`); the dialog closes, its fields are cleared and a "Password changed" toast shows (`ProfileView.vue`).
5. Failure: the dialog stays open and shows the server's reason in a banner at its top (`ProfileView.vue`): "Current password is incorrect" (400, code `USER_003`) or the password rule text (400, code `USER_004`). Both are ProblemDetail responses.
6. Closing the dialog by any route (Cancel, X, Escape, backdrop, success) clears the fields and the banner (`ProfileView.vue`).

### What a MEMBER user can do
1. Signing in sends a MEMBER to `/my-dues`, their own dues screen ([my-dues.md](my-dues.md)); the name in the phone top bar opens `/profile` (`frontend/src/stores/authStore.js` `homePath`).
2. The rail shows only the account block for MEMBER; the other links need VOLUNTEER (`frontend/src/App.vue`).
3. Typing a staff-only URL shows an "Access denied" warning toast and redirects back to `/my-dues`, with no query parameter (`frontend/src/router/index.js`).
4. They can edit their profile, change their password, and sign out; "Edit my details" and "Change password" on My dues link here.

## Rules
- Server, first and last name: max 50 chars each, no minimum (`UpdateUserProfileRequest.java`).
- Server, phone: regex `^\+?[0-9\s\-\(\)]{10,}$` (`UpdateUserProfileRequest.java`); a blank phone (`""` or spaces) is turned into null by the setter and skips the check.
- Server, bio: max 500 (`UpdateUserProfileRequest.java`).
- A blank or null first/last name is ignored and the old value is kept; phone and bio are overwritten, null clears them (`User.java`).
- Client: first and last name required (the form insists on a name even though the server only ignores blank ones), no minimum, 50 characters via `maxlength` (`ProfileView.vue`); phone optional and checked with the server's exact pattern (`frontend/src/utils/phoneRules.js`, `ProfileView.vue`); bio max 500.
- Password strength lives in the domain (`User.validatePasswordStrength`, `User.java`): at least 8 characters, at most 72 UTF-8 bytes (BCrypt's limit), one lowercase letter, one uppercase letter, one digit and one special character. Special means anything that is not a letter, digit or whitespace, so `-`, `_`, `#`, `.` all count; spaces are allowed (passphrases) but do not count as special. One message for every failure: "Password must be 8 to 72 characters (bytes) and contain an uppercase letter, a lowercase letter, a digit and a special character". The client repeats the same rule (`frontend/src/utils/passwordRules.js`).
- DTO password rule: current password only not blank (the seeded admin's is `admin`, 5 characters); new password not blank and 8-72 characters (`src/main/java/io/github/membertracker/infrastructure/dto/ChangePasswordRequest.java`).
- Changing the password resets failed-login attempts to 0 and stamps `lastPasswordChange` (`User.java`). A wrong current password counts toward the account lock (`ChangePasswordUseCase.java`).

## Known issues
- Changing the password ends every other session: tokens issued before the change are rejected. The response sets fresh `sid` / `sid_refresh` cookies so this browser stays signed in (`UserController.java`).
- No password reset; a wrong current password counts toward the 15-minute account lock; see [../authentication.md](../authentication.md) known gaps and audit user-management item in [../functionality-audit.md](../functionality-audit.md).
- The page shows no real account status or join date: the API has none to give (`UserResponseDto` has no `createdAt`).

Fixed since the first version of this page: a failed save no longer replaces the page with one red message (server field errors show under their fields and the form stays); the password dialog is a `BaseModal`, not Bootstrap's.

## Related
- [user-controller.md](user-controller.md): endpoints, DTOs, errors
- [profile-view.md](profile-view.md): page state, actions, client validation
- [login-view.md](login-view.md): where sign-in sends each role
- [../authentication.md](../authentication.md#passwords-and-lockout): password hashing, rule, lockout
- [../functionality-audit.md](../functionality-audit.md): Member role scope and account gaps
