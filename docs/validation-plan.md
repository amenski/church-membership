# API Validation Plan

## Current State
- Project uses Spring Boot 3.4.5 with Jakarta Validation
- `spring-boot-starter-validation` dependency is already included
- Member and Payment domain models have some validation annotations, but they're NOT enforced at controller level

## Endpoints Requiring Validation

### MemberController (`/api/members`)
| Method | Endpoint | Request Body/Params | Validation Needed |
|--------|----------|---------------------|-------------------|
| GET | `/{id}` | PathVariable (Long) | @Positive |
| POST | `/` | Member | @Valid on body |
| PUT | `/{id}` | PathVariable + Member | @Valid on body |
| DELETE | `/{id}` | PathVariable (Long) | @Positive |
| GET | `/overdue/{months}` | PathVariable (int) | @Min(1) |

### PaymentController (`/api/payments`)
| Method | Endpoint | Request Body/Params | Validation Needed |
|--------|----------|---------------------|-------------------|
| GET | `/{id}` | PathVariable (Long) | @Positive |
| GET | `/member/{memberId}` | PathVariable (Long) | @Positive |
| POST | `/` | Payment | @Valid on body |
| DELETE | `/{id}` | PathVariable (Long) | @Positive |

### CommunicationController (`/api/communications`)
| Method | Endpoint | Request Body/Params | Validation Needed |
|--------|----------|---------------------|-------------------|
| GET | `/{id}` | PathVariable (Long) | @Positive |
| POST | `/` | Communication | @Valid + title required |
| POST | `/send-to-all` | Communication | @Valid + title required |
| POST | `/send-to-overdue/{months}` | PathVariable + Communication | @Min(1), @Valid |

### UserController (`/api/users`)
| Method | Endpoint | Request Body/Params | Validation Needed |
|--------|----------|---------------------|-------------------|
| PUT | `/me/profile` | UpdateUserProfileRequest | @Valid, phone pattern |
| PUT | `/me/password` | ChangePasswordRequest | @Valid, password min length |

## DTO Validation

### ChangePasswordRequest
- `currentPassword`: @NotBlank, @Size(min=6)
- `newPassword`: @NotBlank, @Size(min=6, max=100)

### UpdateUserProfileRequest
- `phone`: @Pattern(regexp = "^\\+?[0-9\\s\\-\\(\\)]{10,}$")
- `firstName`: @Size(max=50)
- `lastName`: @Size(max=50)
- `bio`: @Size(max=500)

## Implementation Tasks

1. **Create Global Exception Handler** - Handle MethodArgumentNotValidException
2. **Update MemberController** - Add @Valid, validate path variables
3. **Update PaymentController** - Add @Valid, validate path variables
4. **Update CommunicationController** - Add @Valid, validate path variables
5. **Update UserController** - Add @Valid to DTOs
6. **Add validation annotations to DTOs**
7. **Add @Valid to domain models used in request bodies**

## Notes
- DashboardController has no POST/PUT endpoints, only GET - no validation needed
- Security is handled separately via Spring Security
