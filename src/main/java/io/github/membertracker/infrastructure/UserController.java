package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.membertracker.infrastructure.dto.ChangePasswordRequest;
import io.github.membertracker.infrastructure.dto.UpdateUserProfileRequest;
import io.github.membertracker.infrastructure.dto.UserResponseDto;
import io.github.membertracker.infrastructure.handler.ProblemDetails;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.utils.CookieUtils;
import io.github.membertracker.utils.JwtUtils;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.UpdateUserProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "The signed-in user: profile and password.")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final CookieUtils cookieUtils;
    private final AuthProperties authProperties;

    public UserController(GetCurrentUserUseCase getCurrentUserUseCase,
                         UpdateUserProfileUseCase updateUserProfileUseCase,
                         ChangePasswordUseCase changePasswordUseCase,
                         CookieUtils cookieUtils, AuthProperties authProperties) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.cookieUtils = cookieUtils;
        this.authProperties = authProperties;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user (any signed-in user)")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return unauthorized();
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        return ResponseEntity.ok(getCurrentUserUseCase.execute(userDetails.getUsername()));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update own profile (any signed-in user)")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return unauthorized();
        }

        User userDetails = (User) authentication.getPrincipal();

        User updatedUser = updateUserProfileUseCase.execute(
            userDetails.getId(),
            request.getFirstName(),
            request.getLastName(),
            request.getPhone(),
            request.getBio()
        );

        return ResponseEntity.ok(new UserResponseDto(
            updatedUser.getId(),
            updatedUser.getEmailValue(),
            updatedUser.isEnabled(),
            updatedUser.getRole().name(),
            updatedUser.getFirstName(),
            updatedUser.getLastName(),
            updatedUser.getPhone(),
            updatedUser.getBio()
        ));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change own password (any signed-in user); ends every other session and sets fresh cookies for this one")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return unauthorized();
        }

        User userDetails = (User) authentication.getPrincipal();

        changePasswordUseCase.execute(
            userDetails.getId(),
            request.getCurrentPassword(),
            request.getNewPassword()
        );

        // Tokens issued before the change are now rejected. Give this browser fresh ones so it stays signed in.
        long authTime = Instant.now().getEpochSecond();
        String accessToken = JwtUtils.generateAccessToken(userDetails, authProperties.getJwtSecret(), authProperties.getAccessTtlSeconds(), authTime);
        String refreshToken = JwtUtils.generateRefreshToken(userDetails, authProperties.getJwtSecret(), authProperties.getRefreshTtlSeconds(), authTime);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieUtils.buildAccessCookie(accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, cookieUtils.buildRefreshCookie(refreshToken).toString())
            .body(Map.of("message", "Password changed successfully"));
    }

    private static ResponseEntity<ProblemDetail> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}
