package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The member-request fixture is shared with the frontend test
 * (frontend/src/__tests__/utils/memberPayload.test.js): both sides must agree on the body shape.
 */
@WebMvcTest(controllers = MemberController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class MemberContractTest {

    private static final LocalDate JOIN = LocalDate.of(2025, 1, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetAllMembersUseCase getAllMembersUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetActiveMembersUseCase getActiveMembersUseCase;
    @MockitoBean private GetInactiveMembersUseCase getInactiveMembersUseCase;
    @MockitoBean private SaveMemberUseCase saveMemberUseCase;
    @MockitoBean private UpdateMemberUseCase updateMemberUseCase;
    @MockitoBean private DeleteMemberUseCase deleteMemberUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    private static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts/member-request.json"));
    }

    @Test
    void postFixtureIsAcceptedAndReachesTheUseCase() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke("Test Member", "member@example.com", "+39 333 1234567", JOIN);
    }

    @Test
    void putFixtureIsAcceptedAndReachesTheUseCase() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), eq(true));
    }

    @Test
    void putForUnknownIdIsNotFound() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/members/99").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isNotFound());
    }

    @Test
    void systemManagedFieldsInTheBodyAreIgnored() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));
        String body = fixture().replace("}", ", \"id\": 99, \"consecutiveMonthsMissed\": 7, "
            + "\"lastPaymentDate\": \"2020-01-01\", \"lastMissedCountMonth\": \"2026-09\"}");

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        // the use case has no parameter for these fields, and the id comes from the path only
        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), eq(true));

        when(saveMemberUseCase.invoke(any(), any(), any(), any())).thenReturn(new Member());
        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
        verify(saveMemberUseCase).invoke("Test Member", "member@example.com", "+39 333 1234567", JOIN);
    }

    @Test
    void blankPhoneIsAcceptedAndBecomesNull() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("+39 333 1234567", "")))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke("Test Member", "member@example.com", null, JOIN);
    }

    @Test
    void blankNameAndMalformedEmailAreRejectedWithoutEchoingValues() throws Exception {
        String body = "{\"name\": \"\", \"email\": \"not-an-email-xyz\"}";

        String response = mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("not-an-email-xyz");
        verify(saveMemberUseCase, never()).invoke(any(), any(), any(), any());
    }
}
