package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
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
import static org.mockito.ArgumentMatchers.isNull;
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
    @MockitoBean private RecordActivityUseCase recordActivityUseCase;
    @MockitoBean private GetAllMembersUseCase getAllMembersUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetActiveMembersUseCase getActiveMembersUseCase;
    @MockitoBean private GetInactiveMembersUseCase getInactiveMembersUseCase;
    @MockitoBean private GetArchivedMembersUseCase getArchivedMembersUseCase;
    @MockitoBean private SaveMemberUseCase saveMemberUseCase;
    @MockitoBean private UpdateMemberUseCase updateMemberUseCase;
    @MockitoBean private ArchiveMemberUseCase archiveMemberUseCase;
    @MockitoBean private DeleteMemberPermanentlyUseCase deleteMemberPermanentlyUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    private static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts/member-request.json"));
    }

    @org.junit.jupiter.api.BeforeEach
    void theMemberBeingEditedExists() {
        when(getMemberByIdUseCase.invoke(any())).thenReturn(Optional.of(new Member()));
    }

    @Test
    void postFixtureIsAcceptedAndReachesTheUseCase() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke(eq("Test Member"), eq("member@example.com"), eq("+39 333 1234567"), eq(JOIN), eq(MemberStatus.MEMBER));
    }

    @Test
    void putFixtureIsAcceptedAndReachesTheUseCase() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), isNull(), eq(true));
    }

    @Test
    void putForUnknownIdIsNotFound() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/members/99").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isNotFound());
    }

    @Test
    void systemManagedFieldsInTheBodyAreIgnored() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));
        String body = fixture().replace("}", ", \"id\": 99, \"consecutiveMonthsMissed\": 7, "
            + "\"lastPaymentDate\": \"2020-01-01\", \"lastMissedCountMonth\": \"2026-09\"}");

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        // the use case has no parameter for these fields, and the id comes from the path only
        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), isNull(), eq(true));

        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());
        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
        verify(saveMemberUseCase).invoke(eq("Test Member"), eq("member@example.com"), eq("+39 333 1234567"), eq(JOIN), eq(MemberStatus.MEMBER));
    }

    @Test
    void blankPhoneIsAcceptedAndBecomesNull() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("+39 333 1234567", "")))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke(eq("Test Member"), eq("member@example.com"), isNull(), eq(JOIN), eq(MemberStatus.MEMBER));
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
        verify(saveMemberUseCase, never()).invoke(any(), any(), any(), any(), any());
    }

    @Test
    void aBodyWithoutAnEmailIsAcceptedAndReachesTheUseCaseWithNull() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());
        String withoutEmail = "{\"name\": \"Test Child\", \"joinDate\": \"2025-01-15\"}";

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(withoutEmail))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke(eq("Test Child"), isNull(), isNull(), eq(JOIN), isNull());
    }

    @Test
    void aBlankEmailBecomesNullAndAnEmailIsTrimmedOnUpdate() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("member@example.com", "   ")))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/members/2").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("member@example.com", " member@example.com ")))
            .andExpect(status().isOk());

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq(null),
            eq("+39 333 1234567"), eq(JOIN), isNull(), eq(true));
        verify(updateMemberUseCase).invoke(eq(2L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), isNull(), eq(true));
    }

    @Test
    void theStatusInTheBodyReachesTheUseCaseAndActiveIsStillAccepted() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));

        mockMvc.perform(put("/api/members/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("\"active\": true", "\"status\": \"TRANSFERRED\"")))
            .andExpect(status().isOk());

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), eq("member@example.com"),
            eq("+39 333 1234567"), eq(JOIN), eq(MemberStatus.TRANSFERRED), isNull());
    }

    @Test
    void createMapsTheLegacyActiveFlagToAStatus() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("\"active\": true", "\"active\": false")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("\"active\": true", "\"active\": true, \"status\": \"INACTIVE\"")))
            .andExpect(status().isOk());

        verify(saveMemberUseCase, org.mockito.Mockito.times(2))
            .invoke(eq("Test Member"), eq("member@example.com"), eq("+39 333 1234567"), eq(JOIN), eq(MemberStatus.INACTIVE));
    }

    @Test
    void anUnknownStatusIsRejected() throws Exception {
        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("\"active\": true", "\"status\": \"RETIRED\"")))
            .andExpect(status().isBadRequest());

        verify(saveMemberUseCase, never()).invoke(any(), any(), any(), any(), any());
    }

    @Test
    void aStatusTheUseCaseRefusesIsAFieldErrorOnStatus() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any()))
            .thenThrow(MemberDomainException.statusNotAllowed("A new member can only be MEMBER or INACTIVE."));

        mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture().replace("\"active\": true", "\"status\": \"ARCHIVED\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MEMBER_009"))
            .andExpect(jsonPath("$.errors[0].field").value("status"))
            .andExpect(jsonPath("$.errors[0].message").value("A new member can only be MEMBER or INACTIVE."));
    }

    @Test
    void theMemberJsonCarriesBothStatusAndActive() throws Exception {
        Member member = new Member("A", null, null);
        member.setId(3L);
        member.setStatus(MemberStatus.DECEASED);
        when(getMemberByIdUseCase.invoke(3L)).thenReturn(Optional.of(member));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/members/3")
                .with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DECEASED"))
            .andExpect(jsonPath("$.active").value(false));
    }
}
