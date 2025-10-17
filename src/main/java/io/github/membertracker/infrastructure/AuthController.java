package io.github.membertracker.infrastructure;

import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.usecase.AuthenticateUserUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.RegisterUserUseCase;
import io.github.membertracker.utils.CookieUtils;
import io.github.membertracker.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    private final CookieUtils cookieUtils;
    private final AuthProperties authProperties;

    public AuthController(AuthenticateUserUseCase authenticateUserUseCase,
                         RegisterUserUseCase registerUserUseCase,
                         LoadUserByUsernameUseCase loadUserByUsernameUseCase,
                         CookieUtils cookieUtils, AuthProperties authProperties) {
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.registerUserUseCase = registerUserUseCase;
        this.loadUserByUsernameUseCase = loadUserByUsernameUseCase;
        this.cookieUtils = cookieUtils;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest loginRequest) {
        try {
            var user = authenticateUserUseCase.invoke(loginRequest.getEmail(), loginRequest.getPassword());

            String accessToken = JwtUtils.generateAccessToken(user, authProperties.getJwtSecret(), authProperties.getAccessTtlSeconds());
            String refreshToken = JwtUtils.generateRefreshToken(user, authProperties.getJwtSecret(), authProperties.getRefreshTtlSeconds());

            ResponseCookie accessCookie = cookieUtils.buildAccessCookie(accessToken);
            ResponseCookie refreshCookie = cookieUtils.buildRefreshCookie(refreshToken);

            // Return user data in response body
            Map<String, Object> response = Map.of(
                "user", Map.of(
                    "email", user.getUsername(),
                    "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .orElse("USER")
                )
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody RegisterRequest registerRequest) {
        try {
            var user = registerUserUseCase.invoke(
                registerRequest.getEmail(),
                registerRequest.getPassword(),
                registerRequest.getFirstName(),
                registerRequest.getLastName()
            );

            String accessToken = JwtUtils.generateAccessToken(user, authProperties.getJwtSecret(), authProperties.getAccessTtlSeconds());
            String refreshToken = JwtUtils.generateRefreshToken(user, authProperties.getJwtSecret(), authProperties.getRefreshTtlSeconds());

            ResponseCookie accessCookie = cookieUtils.buildAccessCookie(accessToken);
            ResponseCookie refreshCookie = cookieUtils.buildRefreshCookie(refreshToken);

            // Return user data in response body
            Map<String, Object> response = Map.of(
                "user", Map.of(
                    "email", user.getUsername(),
                    "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .orElse("USER")
                )
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(HttpServletRequest request) {
        try {
            String refreshToken = getRefreshTokenFromCookie(request);
            if (refreshToken == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Refresh token not found"));
            }
            
            if (!JwtUtils.validateToken(refreshToken, authProperties.getJwtSecret())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid refresh token"));
            }
            
            String username = JwtUtils.extractUsername(refreshToken, authProperties.getJwtSecret());
            UserDetails user = loadUserByUsernameUseCase.invoke(username);
            
            String newAccessToken = JwtUtils.generateAccessToken(user, authProperties.getJwtSecret(), authProperties.getAccessTtlSeconds());
            String newRefreshToken = JwtUtils.generateRefreshToken(user, authProperties.getJwtSecret(), authProperties.getRefreshTtlSeconds());
            
            ResponseCookie accessCookie = cookieUtils.buildAccessCookie(newAccessToken);
            ResponseCookie refreshCookie = cookieUtils.buildRefreshCookie(newRefreshToken);
            
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .build();
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token refresh failed"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Clear both cookies
        ResponseCookie clearAccessCookie = cookieUtils.buildClearAccessCookie();
        ResponseCookie clearRefreshCookie = cookieUtils.buildClearRefreshCookie();
        
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
                .build();
    }


    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("sid_refresh".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

     public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RegisterRequest {
        private String email;
        private String password;
        private String firstName;
        private String lastName;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}
