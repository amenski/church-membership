# UserController

`src/main/java/io/github/membertracker/infrastructure/UserController.java`

The signed-in user's own profile and password, for any authenticated role. Base path `/api/users`.

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/users/me` | any signed-in user | none | `UserResponseDto` |
| PUT | `/api/users/me/profile` | any signed-in user | `UpdateUserProfileRequest` | `UserResponseDto` |
| PUT | `/api/users/me/password` | any signed-in user | `ChangePasswordRequest` | `{"message": "Password changed successfully"}` |

- No `@PreAuthorize` on any method; access comes from `anyRequest().authenticated()` (`infrastructure/config/SecurityConfig.java:86`). Roles: see [../authentication.md](../authentication.md).
- The user is always the one in the security context. No id in the path, so no way to touch another user.
- `UserResponseDto`: `id`, `email`, `enabled`, `role`, `firstName`, `lastName`, `phone`, `bio` (`infrastructure/dto/UserResponseDto.java:4-11`). No `createdAt`.

### Request validation
| DTO | Field | Rule |
|-----|-------|------|
| `UpdateUserProfileRequest` | `firstName`, `lastName` | max 50 (`UpdateUserProfileRequest.java:8,11`) |
| | `phone` | regex `^\+?[0-9\s\-\(\)]{10,}$` (`:14`) |
| | `bio` | max 500 (`:17`) |
| `ChangePasswordRequest` | `currentPassword` | not blank, min 6 (`ChangePasswordRequest.java:8-9`) |
| | `newPassword` | not blank, 6-100 (`:12-13`) |

## Actions
- `getCurrentUser` -> reads email from principal, calls `GetCurrentUserUseCase.execute(email)` (`UserController.java:39-62`).
- `updateProfile` -> calls `UpdateUserProfileUseCase.execute(userId, firstName, lastName, phone, bio)`, maps the saved `User` to the DTO (`:64-102`).
- `changePassword` -> calls `ChangePasswordUseCase.execute(userId, current, new)` (`:104-134`).

## Collaborators
- `GetCurrentUserUseCase` `usecase/GetCurrentUserUseCase.java:16`: `findByEmail`, builds the DTO.
- `UpdateUserProfileUseCase` `usecase/UpdateUserProfileUseCase.java:15`: `findById`, `User.updateProfile`, `userRepository.update`.
- `ChangePasswordUseCase` `usecase/ChangePasswordUseCase.java:22`: `@Transactional`; checks current password with `PasswordEncoder.matches` (`:27`), then `User.validatePasswordStrength` (`:32`), `User.changePassword`, `save`.
- Password rule: `User.validatePasswordStrength` (`domain/model/User.java:189`); see [../authentication.md](../authentication.md#passwords-and-lockout).
- Models: `User`, `UserResponseDto`.

## Errors
| Status | Source |
|--------|--------|
| 400 | `@Valid` failure -> `GlobalExceptionHandler` `MethodArgumentNotValidException` (`infrastructure/handler/GlobalExceptionHandler.java:53`), RFC 7807 with field messages |
| 400 | `updateProfile`: any exception from the use case (`UserController.java:99-101`), empty body |
| 400 | `changePassword`: any exception, wrong current password and weak new password included (`:129-133`), body `{"error": "Failed to change password"}` (not ProblemDetail) |
| 401 | no usable authentication (`:44-47`, `:69-72`, `:109-112`), empty body; normally the security filter answers first |
| 500 | `getCurrentUser`: any exception, e.g. user not found (`:57-61`), empty body |

## Side effects
- `updateProfile` writes `phone`/`bio`/names and `updatedAt`.
- `changePassword` writes the password hash, `lastPasswordChange`, `updatedAt`, and resets `failedLoginAttempts` to 0 (`domain/model/User.java:204-209`). Existing sessions/tokens are not revoked.

## Gotchas
- The catch-all blocks in `updateProfile` and `changePassword` hide the cause: "user not found", "wrong password" and "weak password" all return the same 400, so the client cannot tell them apart.
- `getCurrentUser` returns 500, not 404, when the user row is gone (`:57-61`); it also prints the stack trace to stderr.
- `User.updateProfile` ignores blank/null `firstName` and `lastName` (keeps old value) but sets `phone` and `bio` to null when null is sent (`domain/model/User.java:304-311`).
- Password strength allows only `A-Za-z0-9@$!%*?&` (`domain/model/User.java:38`); a password containing any other character (e.g. `-`, `_`, `#`) is rejected even if long and mixed-case. The DTO only requires 6 characters (`ChangePasswordRequest.java:13`), so the 8-character and complexity rules surface only as the generic 400 above.
- `updateProfile` casts the principal to `User` (`:75`) while `getCurrentUser` casts to `UserDetails` (`:50`); both rely on the principal being the domain `User`.
