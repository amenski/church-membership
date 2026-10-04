package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.infrastructure.dto.UserResponseDto;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.utils.CookieUtils;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.UpdateUserProfileUseCase;
import io.github.membertracker.usecase.RecordActivityUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, AuthProperties.class, CookieUtils.class})
class UserControllerTest {

    private static final String WEAK_MESSAGE = "Password must be 8 to 72 characters (bytes) and contain an "
        + "uppercase letter, a lowercase letter, a digit and a special character";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private RecordActivityUseCase recordActivityUseCase;
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
    void successIsRecordedAndAFailedChangeIsNot() throws Exception {
        when(changePasswordUseCase.execute(eq(7L), eq("Wrong-pass1"), any()))
            .thenThrow(UserDomainException.invalidPassword());

        mockMvc.perform(changePassword(body("Wrong-pass1", "Newpass1-x")));
        verifyNoInteractions(recordActivityUseCase);

        mockMvc.perform(changePassword(body("Current-pass1", "Newpass1-x"))).andExpect(status().isOk());
        verify(recordActivityUseCase).record(ActivityType.PASSWORD_CHANGED, "Password was changed", "USER", 7L);
    }

    @Test
    void successSetsFreshSessionCookies() throws Exception {
        var response = mockMvc.perform(changePassword(body("Current-pass1", "Newpass1-x")))
            .andExpect(status().isOk()).andReturn().getResponse();

        var cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies.stream().anyMatch(c -> c.startsWith("sid=") && c.contains("HttpOnly"))).isTrue();
        assertThat(cookies.stream().anyMatch(c -> c.startsWith("sid_refresh=") && c.contains("Path=/api/auth"))).isTrue();
    }

    @Test
    void failedChangeSetsNoCookies() throws Exception {
        when(changePasswordUseCase.execute(eq(7L), eq("Wrong-pass1"), any()))
            .thenThrow(UserDomainException.invalidPassword());

        var response = mockMvc.perform(changePassword(body("Wrong-pass1", "Newpass1-x"))).andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
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

    // profile and /me

    @Test
    void updatingTheProfileWithAnEmptyPhoneGives200AndClearsThePhone() throws Exception {
        when(updateUserProfileUseCase.execute(eq(7L), eq("Ada"), eq("Lovelace"), isNull(), any())).thenReturn(principal);

        mockMvc.perform(put("/api/users/me/profile").with(csrf()).with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"phone\":\"\",\"bio\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("member@example.com"));
    }

    @Test
    void aTooShortPhoneIsRejectedAsAProblem() throws Exception {
        mockMvc.perform(put("/api/users/me/profile").with(csrf()).with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ada\",\"phone\":\"5551234\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").isNotEmpty());
    }

    @Test
    void aUseCaseFailureOnProfileUpdateIsAProblemWithTheReason() throws Exception {
        when(updateUserProfileUseCase.execute(anyLong(), any(), any(), any(), any()))
            .thenThrow(UserDomainException.userNotFound("7"));

        mockMvc.perform(put("/api/users/me/profile").with(csrf()).with(user(principal))
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Ada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void meGives200() throws Exception {
        when(getCurrentUserUseCase.execute("member@example.com")).thenReturn(
            new UserResponseDto(7L, "member@example.com", true, "MEMBER", "Ada", "Lovelace", null, null));

        mockMvc.perform(get("/api/users/me").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("member@example.com"))
            .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void meWithoutSignInGives401Problem() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meForAUserWhoIsGoneIsAProblemNotAnEmpty500() throws Exception {
        when(getCurrentUserUseCase.execute("member@example.com")).thenThrow(UserDomainException.userNotFound("member@example.com"));

        mockMvc.perform(get("/api/users/me").with(user(principal)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").isNotEmpty());
    }
}
