# Profile and password

Every signed-in user views and edits their own name, phone and bio, and changes their own password. For MEMBER users it is the only screen they have.

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| View own profile | MEMBER | `/profile`, `GET /api/users/me` |
| Edit own name, phone, bio | MEMBER | `/profile`, `PUT /api/users/me/profile` |
| Change own password | MEMBER | `PUT /api/users/me/password` (button on `/profile` is dead, see Known issues) |
| Edit email, role or another user | nobody | no UI or API |

- No `@PreAuthorize` on these endpoints; any authenticated user passes (`src/main/java/io/github/membertracker/infrastructure/config/SecurityConfig.java:86`).
- The user always comes from the security context, never from an id in the request (`src/main/java/io/github/membertracker/infrastructure/UserController.java:75-76`, `:115-116`).
- Route `/profile` only needs sign-in (`frontend/src/router/index.js:36-39`).

## How it works
### View own profile
1. Open "Profile" in the top nav; it is shown to every role (`frontend/src/App.vue:24-26`).
2. The page loads `GET /users/me` and fills the form (`frontend/src/views/ProfileView.vue:402-413`).
3. Email is shown but disabled: "Email cannot be changed" (`ProfileView.vue:52`, `:57`). The role appears as a badge (`:10-12`).
4. Success: fields show, disabled until Edit is clicked. Failure: the whole page is replaced by "Failed to load profile" (`:415`, `:24-27`).

### Edit own profile
1. Click Edit (`ProfileView.vue:150`); fields unlock (`:422-426`).
2. Change first name, last name, phone, bio. Client checks run on Save (`:359-394`).
3. Save sends `PUT /users/me/profile` (`:451`, `frontend/src/services/api.js:484-486`). The server validates the DTO (`src/main/java/io/github/membertracker/infrastructure/dto/UpdateUserProfileRequest.java:8-17`), then `User.updateProfile` trims and stores the values (`src/main/java/io/github/membertracker/domain/model/User.java:304-312`).
4. Success: green "Profile updated successfully!" for 5 s, edit mode ends, the signed-in user in the store is replaced (`ProfileView.vue:452-459`). The nav name updates (`App.vue:34`).
5. Failure: the whole page is replaced by "Failed to update profile" with no retry; reload to recover (`ProfileView.vue:460-462`). Cancel restores the last saved values (`:428-438`).

### Change password
1. Intended: Change Password button opens a dialog with current, new and confirm fields (`ProfileView.vue:209-215`, `:226-297`).
2. Intended: client checks current is present, new is at least 8 chars, confirm matches (`:469-498`).
3. Intended: `PUT /users/me/password`. The server checks the current password (`src/main/java/io/github/membertracker/usecase/ChangePasswordUseCase.java:27`), then the strength rule (`:32`), then stores the new hash (`:35-37`).
4. Server success: `{"message": "Password changed successfully"}` (`UserController.java:124-127`); the view would show "Password changed successfully!" (`ProfileView.vue:533`).
5. Failure: any error gives one 400 `{"error": "Failed to change password"}` (`UserController.java:129-133`); the view then replaces the page with "Failed to change password. Please check your current password." (`ProfileView.vue:539`).
6. Today none of this is reachable from the UI: see Known issues.

### What a MEMBER user can do
1. Signing in sends a MEMBER to `/profile` (`frontend/src/stores/authStore.js:43`, `frontend/src/router/index.js:92-93`).
2. Nav shows only Profile for MEMBER; the other links need VOLUNTEER (`App.vue:12-26`).
3. Typing a staff-only URL redirects back to `/profile?error=access_denied` (`router/index.js:102-105`). Nothing on the page reads that query, so no message appears.
4. They can edit their profile; they can sign out via the user menu. They cannot change their password from the UI today.

## Rules
- Server, first and last name: max 50 chars each, no minimum (`UpdateUserProfileRequest.java:8`, `:11`).
- Server, phone: regex `^\+?[0-9\s\-\(\)]{10,}$` (`UpdateUserProfileRequest.java:14`).
- Server, bio: max 500 (`UpdateUserProfileRequest.java:17`).
- A blank or null first/last name is ignored and the old value is kept; phone and bio are overwritten, null clears them (`User.java:304-311`).
- Client: first and last name required, min 2 (`ProfileView.vue:364-379`); phone optional, regex after stripping spaces, `-`, `(`, `)` (`:397-399`); bio max 500 (`:388`).
- Password strength lives in the domain: at least 8 chars, one lowercase, one uppercase, one digit, one of `@$!%*?&`, and no other characters (`User.java:36-38`, `:189-196`).
- DTO password rule: current password min 6; new password 6-100 (`src/main/java/io/github/membertracker/infrastructure/dto/ChangePasswordRequest.java:8-13`).
- Changing the password resets failed-login attempts to 0 and stamps `lastPasswordChange` (`User.java:204-209`). Existing sessions stay valid.

## Known issues
- Change Password button never opens the dialog (STILL TRUE). It only sets `showChangePasswordModal` (`ProfileView.vue:212`); that ref is declared and returned (`:330`, `:560`) but nothing reads it. There is no `data-bs-toggle`, no watcher and no Bootstrap `Modal` call; the `changePasswordModal` ref is never returned from `setup` (`:550-568`). Users cannot change their password from the UI. Backend works (see [profile-view.md](profile-view.md) gotchas).
- Client phone regex disagrees with the server (`ProfileView.vue:398` vs `UpdateUserProfileRequest.java:14`):
  - Empty phone: the form sends `""` for users with no phone (`ProfileView.vue:411`); the server regex needs 10 or more characters, so `""` fails. Saving any profile without a phone fails with the generic error.
  - Short numbers such as `5551234` pass the client and fail the server (needs 10+ chars).
  - Numbers starting with `0` fail the client but would pass the server.
  - Client allows up to 16 digits with no `)`/`-` position rules; server counts formatting characters toward its 10.
- Name length: client requires 2+, server has no minimum; server caps at 50, client has no cap (`ProfileView.vue:367`, `:376`; `UpdateUserProfileRequest.java:8`, `:11`). A 51-char name passes the client and fails the server.
- Password rule mismatch: the dialog hint and client check say only "at least 8 characters" (`ProfileView.vue:264`, `:483`); the server also needs mixed case, a digit, a symbol from `@$!%*?&`, and rejects other symbols (`User.java:38`). The DTO accepts 6 chars (`ChangePasswordRequest.java:13`), so weak passwords reach the domain check. Wrong current password and weak new password return the same 400 (`UserController.java:129-133`), and the view blames the current password (`ProfileView.vue:539`).
- Any save or password failure replaces the whole page with one red message; the form is hidden until reload (`ProfileView.vue:24-27`, `:461`, `:539`). Server field messages are discarded.
- "Member Since" always shows `N/A`: the DTO has no `createdAt` (`src/main/java/io/github/membertracker/infrastructure/dto/UserResponseDto.java:4-11`; `ProfileView.vue:345`).
- "Account Status: Active" is hard-coded, not read from `enabled` (`ProfileView.vue:190-196`).
- "Export Data" button has no handler (`ProfileView.vue:216`).
- `?error=access_denied` redirect shows no message to the MEMBER (`router/index.js:103`; nothing in `ProfileView.vue` reads it).
- `GET /users/me` returns an empty 500 if the user row is gone (`UserController.java:57-61`); profile and password updates return empty/generic 400 for every cause (`:99-101`, `:129-133`).
- No password reset, and locked accounts never unlock; see [../authentication.md](../authentication.md) known gaps and audit user-management item in [../functionality-audit.md](../functionality-audit.md).

## Related
- [user-controller.md](user-controller.md): endpoints, DTOs, errors
- [profile-view.md](profile-view.md): page state, actions, client validation
- [login-view.md](login-view.md): where sign-in sends each role
- [../authentication.md](../authentication.md#passwords-and-lockout): password hashing, rule, lockout
- [../functionality-audit.md](../functionality-audit.md): Member role scope and account gaps
