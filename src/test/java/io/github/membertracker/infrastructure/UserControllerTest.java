package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.UpdateUserProfileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class UserControllerTest {

    private static final String WEAK_MESSAGE = "Password must be 8 to 72 characters (bytes) and contain an "
        + "uppercase letter, a lowercase letter, a digit and a special character";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
    @MockitoBean private UpdateUserProfileUseCase updateUserProfileUseCase;
    @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

    private User principal;

    @BeforeEach
    void setUp() {
        principal = new User(Email.of("member@example.com"), "hash", UserRole.MEMBER);
        principal.setId(7L);
    }

    private MockHttpServletRequestBuilder changePassword(String body) {
        return put("/api/users/me/password").with(csrf()).with(user(principal))
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    @Test
    void wrongCurrentPasswordGivesA400WithThatReason() throws Exception {
        when(changePasswordUseCase.execute(eq(7L), eq("Wrong-pass1"), any()))
            .thenThrow(UserDomainException.invalidPassword());

        mockMvc.perform(changePassword(body("Wrong-pass1", "Newpass1-x")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    @Test
    void weakNewPasswordGivesA400WithTheStrengthMessage() throws Exception {
        when(changePasswordUseCase.execute(anyLong(), any(), eq("alllowercase1")))
            .thenThrow(UserDomainException.weakPassword(WEAK_MESSAGE));

        mockMvc.perform(changePassword(body("Current-pass1", "alllowercase1")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(WEAK_MESSAGE));
    }

    @Test
    void aFiveCharacterCurrentPasswordPassesValidation() throws Exception {
        // the seeded admin's password is "admin" (5 characters)
        mockMvc.perform(changePassword(body("admin", "Newpass1-x")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void aNewPasswordOver72CharactersIsRejectedByValidation() throws Exception {
        mockMvc.perform(changePassword(body("admin", "Aa1-" + "x".repeat(70))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')]").isNotEmpty());
        verifyNoInteractions(changePasswordUseCase);
    }

    @Test
    void successGives200() throws Exception {
        mockMvc.perform(changePassword(body("Current-pass1", "Newpass1-x")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void noPrincipalGives401Problem() throws Exception {
        mockMvc.perform(put("/api/users/me/password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("Current-pass1", "Newpass1-x")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Authentication required"));
        verifyNoInteractions(changePasswordUseCase);
    }
}
