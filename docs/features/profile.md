# Profile and password

Every signed-in user views and edits their own name, phone and bio, and changes their own password. For MEMBER users it is the only screen they have.

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| View own profile | MEMBER | `/profile`, `GET /api/users/me` |
| Edit own name, phone, bio | MEMBER | `/profile`, `PUT /api/users/me/profile` |
| Change own password | MEMBER | `/profile` (Change Password dialog), `PUT /api/users/me/password` |
| Edit email, role or another user | nobody | no UI or API |

- No `@PreAuthorize` on these endpoints; any authenticated user passes (`src/main/java/io/github/membertracker/infrastructure/config/SecurityConfig.java:130`).
- The user always comes from the security context, never from an id in the request (`src/main/java/io/github/membertracker/infrastructure/UserController.java:52`, `:67-70`, `:99-102`).
- Route `/profile` only needs sign-in (`frontend/src/router/index.js:37-40`).

## How it works
### View own profile
1. Open "Profile" in the top nav; it is shown to every role (`frontend/src/App.vue:24-26`).
2. The page loads `GET /users/me` and fills the form (`frontend/src/views/ProfileView.vue:363-374`).
3. Email is shown but disabled: "Email cannot be changed" (`ProfileView.vue:52`, `:57`). The role appears as a badge (`:10-12`). The Account Information card holds only the Change Password button.
4. Success: fields show, disabled until Edit is clicked. Failure: the whole page is replaced by "Failed to load profile" (`:376`, `:24-27`).

### Edit own profile
1. Click Edit (`ProfileView.vue:152`); fields unlock (`:383-387`).
2. Change first name, last name, phone, bio. Client checks run on Save (`:332-361`). The name inputs stop at 50 characters (`maxlength`, `:72`, `:93`).
3. Save sends `PUT /users/me/profile` (`:412`, `frontend/src/services/api.js:484-486`). The server turns a blank phone into null, validates the DTO (`src/main/java/io/github/membertracker/infrastructure/dto/UpdateUserProfileRequest.java:8-17`, setter `:49-52`), then `User.updateProfile` trims and stores the values (`src/main/java/io/github/membertracker/domain/model/User.java:317-327`).
4. Success: green "Profile updated successfully!" for 5 s, edit mode ends, the signed-in user in the store is replaced (`ProfileView.vue:413-420`). The nav name updates (`App.vue:34`).
5. Failure: the whole page is replaced by "Failed to update profile" with no retry; reload to recover (`ProfileView.vue:421-423`). Cancel restores the last saved values (`:389-399`).

### Change password
1. The Change Password button opens a dialog through Bootstrap's data API (`data-bs-toggle="modal"`, `frontend/src/views/ProfileView.vue:195-196`, dialog `:208`) with current, new and confirm fields.
2. Client checks on submit: current is present, new follows the password rule (`frontend/src/utils/passwordRules.js`, same rule as the server), confirm matches (`ProfileView.vue:430-462`).
3. `PUT /users/me/password`. The server checks the current password (`src/main/java/io/github/membertracker/usecase/ChangePasswordUseCase.java:27`), then the strength rule (`:32`), then stores the new hash (`:35-37`).
4. Success: `{"message": "Password changed successfully"}` (`src/main/java/io/github/membertracker/infrastructure/UserController.java:101-107`); the dialog closes, its fields are cleared and a "Password changed" toast shows (`ProfileView.vue:494-501`).
5. Failure: the dialog stays open and shows the server's reason in a red alert (`ProfileView.vue:503-505`): "Current password is incorrect" (400, code `USER_003`) or the password rule text (400, code `USER_004`). Both are ProblemDetail responses.
6. Closing the dialog by any route (Cancel, X, Escape, backdrop) clears the fields and the alert (`hidden.bs.modal`, `ProfileView.vue:513`).

### What a MEMBER user can do
1. Signing in sends a MEMBER to `/profile` (`frontend/src/stores/authStore.js:43`, `frontend/src/router/index.js:93-94`).
2. Nav shows only Profile for MEMBER; the other links need VOLUNTEER (`App.vue:12-26`).
3. Typing a staff-only URL shows an "Access denied" warning toast and redirects back to `/profile`, with no query parameter (`router/index.js:103-112`).
4. They can edit their profile, change their password, and sign out via the user menu.

## Rules
- Server, first and last name: max 50 chars each, no minimum (`UpdateUserProfileRequest.java:8`, `:11`).
- Server, phone: regex `^\+?[0-9\s\-\(\)]{10,}$` (`UpdateUserProfileRequest.java:14`); a blank phone (`""` or spaces) is turned into null by the setter and skips the check (`:49-52`).
- Server, bio: max 500 (`UpdateUserProfileRequest.java:17`).
- A blank or null first/last name is ignored and the old value is kept; phone and bio are overwritten, null clears them (`User.java:317-326`).
- Client: first and last name required (the form insists on a name even though the server only ignores blank ones), no minimum, 50 characters via `maxlength` (`ProfileView.vue:336-346`); phone optional and checked with the server's exact pattern (`frontend/src/utils/phoneRules.js`, `ProfileView.vue:349-352`); bio max 500 (`:355`).
- Password strength lives in the domain (`User.validatePasswordStrength`, `src/main/java/io/github/membertracker/domain/model/User.java:196-210`): at least 8 characters, at most 72 UTF-8 bytes (BCrypt's limit), one lowercase letter, one uppercase letter, one digit and one special character. Special means anything that is not a letter, digit or whitespace, so `-`, `_`, `#`, `.` all count; spaces are allowed (passphrases) but do not count as special. One message for every failure: "Password must be 8 to 72 characters (bytes) and contain an uppercase letter, a lowercase letter, a digit and a special character". The client repeats the same rule (`frontend/src/utils/passwordRules.js`).
- DTO password rule: current password only not blank (the seeded admin's is `admin`, 5 characters); new password not blank and 8-72 characters (`src/main/java/io/github/membertracker/infrastructure/dto/ChangePasswordRequest.java:8-13`).
- Changing the password resets failed-login attempts to 0 and stamps `lastPasswordChange` (`User.java:218-223`). Existing sessions stay valid.

## Known issues
- Any profile save failure replaces the whole page with one red message; the form is hidden until reload (`ProfileView.vue:24-27`). Server field messages are discarded. (Password errors no longer do this: they show inside the dialog.)
- Changing the password does not end other sessions: existing access and refresh tokens stay valid (`User.changePassword`, `User.java:218-223`, only updates the hash).
- No password reset, and locked accounts never unlock; see [../authentication.md](../authentication.md) known gaps and audit user-management item in [../functionality-audit.md](../functionality-audit.md).

## Related
- [user-controller.md](user-controller.md): endpoints, DTOs, errors
- [profile-view.md](profile-view.md): page state, actions, client validation
- [login-view.md](login-view.md): where sign-in sends each role
- [../authentication.md](../authentication.md#passwords-and-lockout): password hashing, rule, lockout
- [../functionality-audit.md](../functionality-audit.md): Member role scope and account gaps
