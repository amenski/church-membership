package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.User;
import io.github.membertracker.infrastructure.dto.ChangePasswordRequest;
import io.github.membertracker.infrastructure.dto.UpdateUserProfileRequest;
import io.github.membertracker.infrastructure.dto.UserResponseDto;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.UpdateUserProfileUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    public UserController(GetCurrentUserUseCase getCurrentUserUseCase,
                         UpdateUserProfileUseCase updateUserProfileUseCase,
                         ChangePasswordUseCase changePasswordUseCase) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return ResponseEntity.status(401).body(null);
        }

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String email = userDetails.getUsername();

            UserResponseDto userResponse = getCurrentUserUseCase.execute(email);

            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserResponseDto> updateProfile(@RequestBody UpdateUserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return ResponseEntity.status(401).body(null);
        }

        try {
            User userDetails = (User) authentication.getPrincipal();
            Long userId = userDetails.getId();

            User updatedUser = updateUserProfileUseCase.execute(
                userId,
                request.getFirstName(),
                request.getLastName(),
                request.getPhone(),
                request.getBio()
            );

            UserResponseDto response = new UserResponseDto(
                updatedUser.getId(),
                updatedUser.getEmailValue(),
                updatedUser.isEnabled(),
                updatedUser.getRole().name(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getPhone(),
                updatedUser.getBio()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getPrincipal() instanceof String) {
            return ResponseEntity.status(401).body(null);
        }

        try {
            User userDetails = (User) authentication.getPrincipal();
            Long userId = userDetails.getId();

            changePasswordUseCase.execute(
                userId,
                request.getCurrentPassword(),
                request.getNewPassword()
            );

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password changed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to change password");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
