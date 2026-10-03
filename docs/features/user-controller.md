# UserController

`src/main/java/io/github/membertracker/infrastructure/UserController.java`

The signed-in user's own profile and password, for any authenticated role. Base path `/api/users`.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/users/me` | any signed-in user | none | `UserResponseDto` |
| PUT | `/api/users/me/profile` | any signed-in user | `UpdateUserProfileRequest` | `UserResponseDto` |
| PUT | `/api/users/me/password` | any signed-in user | `ChangePasswordRequest` | `{"message": "Password changed successfully"}` and fresh `sid` / `sid_refresh` cookies |

- No `@PreAuthorize` on any method; access comes from `anyRequest().authenticated()` (`infrastructure/config/SecurityConfig.java:130`). Roles: see [../authentication.md](../authentication.md).
- The user is always the one in the security context. No id in the path, so no way to touch another user.
- `UserResponseDto`: `id`, `email`, `enabled`, `role`, `firstName`, `lastName`, `phone`, `bio` (`infrastructure/dto/UserResponseDto.java:4-11`). No `createdAt`.

### Request validation
| DTO | Field | Rule |
|-----|-------|------|
| `UpdateUserProfileRequest` | `firstName`, `lastName` | max 50 (`UpdateUserProfileRequest.java:8,11`) |
| | `phone` | regex `^\+?[0-9\s\-\(\)]{10,}$` (`:14`); a blank phone is set to null by the setter (`:49-52`), so `""` passes |
| | `bio` | max 500 (`:17`) |
| `ChangePasswordRequest` | `currentPassword` | not blank only (the seeded admin's is 5 characters) (`ChangePasswordRequest.java:8-9`) |
| | `newPassword` | not blank, 8-72 characters (`:11-12`); the full rule is checked in the domain |

## Actions
- `getCurrentUser` -> reads email from principal, calls `GetCurrentUserUseCase.execute(email)` (`UserController.java:42-55`); no try/catch.
- `updateProfile` -> calls `UpdateUserProfileUseCase.execute(userId, firstName, lastName, phone, bio)`, maps the saved `User` to the DTO (`:57-87`); no try/catch.
- `changePassword` -> calls `ChangePasswordUseCase.execute(userId, current, new)`; a wrong current password counts toward the same 5-failure, 15-minute lock as a wrong sign-in password (and a locked account is refused without a check); the new password is stored with a single-row update. Returns 200 `{"message": "Password changed successfully"}`; no try/catch, so domain exceptions reach `GlobalExceptionHandler` (`:89-108`).

## Collaborators
- `GetCurrentUserUseCase` `usecase/GetCurrentUserUseCase.java:16`: `findByEmail`, builds the DTO.
- `UpdateUserProfileUseCase` `usecase/UpdateUserProfileUseCase.java:15`: `findById`, `User.updateProfile`, `userRepository.update`.
- `ChangePasswordUseCase` `usecase/ChangePasswordUseCase.java`: no class-level transaction (the failed-attempt count must survive the exception); checks current password with `PasswordEncoder.matches`, then `User.validatePasswordStrength`, `User.changePassword` (change time in whole seconds), `UserRepository.updatePassword`.
- Password rule: `User.validatePasswordStrength` (`domain/model/User.java:196`): 8 characters minimum, 72 UTF-8 bytes maximum, upper, lower, digit and one special character (anything that is not a letter, digit or whitespace; spaces allowed); see [../authentication.md](../authentication.md#passwords-and-lockout).
- Models: `User`, `UserResponseDto`.

## Errors
| Status | Source |
|--------|--------|
| 400 | `@Valid` failure -> `GlobalExceptionHandler` `MethodArgumentNotValidException` (`infrastructure/handler/GlobalExceptionHandler.java:53`), RFC 7807 with field messages |
| 400 | `updateProfile` / `getCurrentUser`: a `UserDomainException` from the use case (for example user not found) -> `GlobalExceptionHandler` ProblemDetail with the message in `detail` and a `code` |
| 400 | `changePassword`: `UserDomainException` -> `GlobalExceptionHandler` ProblemDetail with the real reason in `detail`: "Current password is incorrect" (`code` `USER_003`) or "Password must be 8 to 72 characters (bytes) and contain an uppercase letter, a lowercase letter, a digit and a special character" (`code` `USER_004`) |
| 401 | the `sid` token is valid but its user was deleted, disabled or locked: the filter treats the request as signed out and the entry point answers (`infrastructure/filter/JwtAuthenticationFilter.java:62-66`) |
| 401 | each method: no usable principal (`:47-50`, `:62-65`, `:94-97`) -> ProblemDetail "Authentication required" (`unauthorized()`, `:110`); normally the security filter answers first |

## Side effects
- `updateProfile` writes `phone`/`bio`/names and `updatedAt`.
- `changePassword` writes the password hash, `lastPasswordChange`, `updatedAt`, and resets `failedLoginAttempts` to 0 (`domain/model/User.java:218-223`). Every session that started before the change is rejected (token `iat` older than `lastPasswordChange`); the controller sets fresh `sid` and `sid_refresh` cookies on the success response so the caller stays signed in.

## Gotchas
- There are no catch-all blocks any more: every failure reaches `GlobalExceptionHandler`. A user row that is gone is a 400 ProblemDetail (domain exceptions all map to 400), not an empty 500; a session for a user deleted after sign-in is already a 401 at the filter.
- `phone` `""` is accepted and clears the phone.
- `User.updateProfile` ignores blank/null `firstName` and `lastName` (keeps old value) but sets `phone` and `bio` to null when null is sent (`domain/model/User.java:317-326`).
- `updateProfile` casts the principal to `User` (`:67`) while `getCurrentUser` casts to `UserDetails` (`:52`); both rely on the principal being the domain `User`.
