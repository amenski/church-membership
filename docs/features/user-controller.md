# UserController

`src/main/java/io/github/membertracker/infrastructure/UserController.java`

The signed-in user's own profile and password, for any authenticated role. Base path `/api/users`.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/users/me` | any signed-in user | none | `UserResponseDto` |
| PUT | `/api/users/me/profile` | any signed-in user | `UpdateUserProfileRequest` | `UserResponseDto` |
| PUT | `/api/users/me/language` | any signed-in user | `UpdateUserLanguageRequest` | `UserResponseDto` |
| PUT | `/api/users/me/password` | any signed-in user | `ChangePasswordRequest` | `{"message": "Password changed successfully"}` and fresh `sid` / `sid_refresh` cookies |

- No `@PreAuthorize` on any method; access comes from `anyRequest().authenticated()` (`infrastructure/config/SecurityConfig.java`). Roles: see [../authentication.md](../authentication.md).
- The user is always the one in the security context. No id in the path, so no way to touch another user.
- `UserResponseDto`: `id`, `email`, `enabled`, `role`, `firstName`, `lastName`, `phone`, `bio`, `language` (`infrastructure/dto/UserResponseDto.java`). No `createdAt`.
- Language is its **own endpoint**, not a field of the profile PUT: that endpoint reads an absent field as "clear this", so sending only a language through it would wipe `phone` and `bio`.

### Request validation
| DTO | Field | Rule |
|-----|-------|------|
| `UpdateUserProfileRequest` | `firstName`, `lastName` | max 50 (`UpdateUserProfileRequest.java`) |
| | `phone` | regex `^\+?[0-9\s\-\(\)]{10,}$`; a blank phone is set to null by the setter, so `""` passes |
| | `bio` | max 500 |
| `UpdateUserLanguageRequest` | `language` | regex `^(am|en)$`; null passes, so a client that omits it changes nothing (`UpdateUserLanguageRequest.java`) |
| `ChangePasswordRequest` | `currentPassword` | not blank only (the seeded admin's is 5 characters) (`ChangePasswordRequest.java`) |
| | `newPassword` | not blank, 8-72 characters; the full rule is checked in the domain |

## Actions
- `getCurrentUser` -> reads email from principal, calls `GetCurrentUserUseCase.execute(email)` (`UserController.java`); no try/catch.
- `updateProfile` -> calls `UpdateUserProfileUseCase.execute(userId, firstName, lastName, phone, bio)`, maps the saved `User` to the DTO; no try/catch.
- `updateLanguage` -> calls `UpdateUserLanguageUseCase.execute(userId, language)`, maps the saved `User` to the DTO; no try/catch.
- `changePassword` -> calls `ChangePasswordUseCase.execute(userId, current, new)`; a wrong current password counts toward the same 5-failure, 15-minute lock as a wrong sign-in password (and a locked account is refused without a check); the new password is stored with a single-row update. Returns 200 `{"message": "Password changed successfully"}`; no try/catch, so domain exceptions reach `GlobalExceptionHandler`.

## Collaborators
- `GetCurrentUserUseCase` `usecase/GetCurrentUserUseCase.java`: `findByEmail`, builds the DTO.
- `UpdateUserProfileUseCase` `usecase/UpdateUserProfileUseCase.java`: `findById`, `User.updateProfile`, `userRepository.update`.
- `UpdateUserLanguageUseCase` `usecase/UpdateUserLanguageUseCase.java`: `findById`, `User.changeLanguage` (which refuses anything but `am`/`en`), `userRepository.update` — and writes nothing when the language is unsupported or unchanged.
- `ChangePasswordUseCase` `usecase/ChangePasswordUseCase.java`: no class-level transaction (the failed-attempt count must survive the exception); checks current password with `PasswordEncoder.matches`, then `User.validatePasswordStrength`, `User.changePassword` (change time in whole seconds), `UserRepository.updatePassword`.
- Password rule: `User.validatePasswordStrength` (`domain/model/User.java`): 8 characters minimum, 72 UTF-8 bytes maximum, upper, lower, digit and one special character (anything that is not a letter, digit or whitespace; spaces allowed); see [../authentication.md](../authentication.md#passwords-and-lockout).
- Models: `User`, `UserResponseDto`.

## Errors
| Status | Source |
|--------|--------|
| 400 | `@Valid` failure -> `GlobalExceptionHandler` `MethodArgumentNotValidException` (`infrastructure/handler/GlobalExceptionHandler.java`), RFC 7807 with field messages |
| 400 | `updateProfile` / `getCurrentUser`: a `UserDomainException` from the use case (for example user not found) -> `GlobalExceptionHandler` ProblemDetail with the message in `detail` and a `code` |
| 400 | `changePassword`: `UserDomainException` -> `GlobalExceptionHandler` ProblemDetail with the real reason in `detail`: "Current password is incorrect" (`code` `USER_003`) or "Password must be 8 to 72 characters (bytes) and contain an uppercase letter, a lowercase letter, a digit and a special character" (`code` `USER_004`) |
| 401 | the `sid` token is valid but its user was deleted, disabled or locked: the filter treats the request as signed out and the entry point answers (`infrastructure/filter/JwtAuthenticationFilter.java`) |
| 401 | each method: no usable principal -> ProblemDetail "Authentication required" (`unauthorized()`); normally the security filter answers first |

## Side effects
- `updateProfile` writes `phone`/`bio`/names and `updatedAt`.
- `updateLanguage` writes `language` and `updatedAt`; it writes nothing when the language is unsupported or already stored.
- `changePassword` writes the password hash, `lastPasswordChange`, `updatedAt`, and resets `failedLoginAttempts` to 0 (`domain/model/User.java`). Every session that started before the change is rejected (token `iat` older than `lastPasswordChange`); the controller sets fresh `sid` and `sid_refresh` cookies on the success response so the caller stays signed in.
- `changePassword` also adds a `PASSWORD_CHANGED` entry to the activity log after the change succeeded (`UserController.java`): [activity.md](activity.md).

## Gotchas
- There are no catch-all blocks any more: every failure reaches `GlobalExceptionHandler`. A user row that is gone is a 400 ProblemDetail (domain exceptions all map to 400), not an empty 500; a session for a user deleted after sign-in is already a 401 at the filter.
- `phone` `""` is accepted and clears the phone.
- `User.updateProfile` ignores blank/null `firstName` and `lastName` (keeps old value) but sets `phone` and `bio` to null when null is sent (`domain/model/User.java`).
- `updateProfile` casts the principal to `User` while `getCurrentUser` casts to `UserDetails`; both rely on the principal being the domain `User`.
- The language the UI ships lives in two places that must stay in step: `User.isSupportedLanguage` (`domain/model/User.java`) and `SUPPORTED_LOCALES` (`frontend/src/i18n.js`).
