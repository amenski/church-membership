package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The optional {@code householdId} of a member request: absent, null, an id, an unknown id; and the member JSON. */
@WebMvcTest(controllers = MemberController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class MemberHouseholdContractTest {

    private static final LocalDate JOIN = LocalDate.of(2025, 1, 15);
    private static final String BASE = "\"name\": \"Test Member\", \"joinDate\": \"2025-01-15\"";

    @Autowired private MockMvc mockMvc;

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

    @BeforeEach
    void theMemberBeingEditedExists() {
        when(getMemberByIdUseCase.invoke(any())).thenReturn(Optional.of(new Member()));
    }

    private void putMember(String body) throws Exception {
        mockMvc.perform(put("/api/members/1").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    @Test
    void aHouseholdIdOnCreateReachesTheUseCase() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{" + BASE + ", \"householdId\": 5}"))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke(eq("Test Member"), isNull(), isNull(), eq(JOIN), isNull(), eq(5L));
    }

    @Test
    void aNullHouseholdIdOnCreateMeansNoHousehold() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Member());

        mockMvc.perform(post("/api/members").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{" + BASE + ", \"householdId\": null}"))
            .andExpect(status().isOk());

        verify(saveMemberUseCase).invoke(eq("Test Member"), isNull(), isNull(), eq(JOIN), isNull());
    }

    @Test
    void anAbsentHouseholdIdOnUpdateLeavesTheHouseholdAlone() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new Member()));

        putMember("{" + BASE + ", \"status\": \"INACTIVE\"}");

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), isNull(), isNull(), eq(JOIN),
            eq(io.github.membertracker.domain.enumeration.MemberStatus.INACTIVE), isNull());
        verify(updateMemberUseCase, never()).invoke(any(), any(), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void anExplicitNullHouseholdIdOnUpdateClearsIt() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
            .thenReturn(Optional.of(new Member()));

        putMember("{" + BASE + ", \"householdId\": null}");

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), isNull(), isNull(), eq(JOIN), isNull(), isNull(),
            eq(true), isNull());
    }

    @Test
    void aHouseholdIdOnUpdateAssignsIt() throws Exception {
        when(updateMemberUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
            .thenReturn(Optional.of(new Member()));

        putMember("{" + BASE + ", \"householdId\": 5}");

        verify(updateMemberUseCase).invoke(eq(1L), eq("Test Member"), isNull(), isNull(), eq(JOIN), isNull(), isNull(),
            eq(true), eq(5L));
    }

    @Test
    void anUnknownHouseholdIsA400FieldErrorOnHouseholdIdWithTheCode() throws Exception {
        when(saveMemberUseCase.invoke(any(), any(), any(), any(), any(), any())).thenThrow(HouseholdDomainException.notFound());

        mockMvc.perform(post("/api/members").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{" + BASE + ", \"householdId\": 987654}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("HOUSEHOLD_001"))
            .andExpect(jsonPath("$.errors[0].field").value("householdId"))
            .andExpect(jsonPath("$.errors[0].message").value("The selected household does not exist."))
            .andExpect(jsonPath("$.detail").value("The selected household does not exist."));
    }

    @Test
    void aNonNumericHouseholdIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/members").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{" + BASE + ", \"householdId\": \"abc\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theMemberJsonCarriesTheHouseholdIdAndName() throws Exception {
        Member member = new Member("A", null, null);
        member.setId(3L);
        member.setHouseholdId(5L);
        member.setHouseholdName("Kebede family");
        Member alone = new Member("B", null, null);
        alone.setId(4L);
        when(getMemberByIdUseCase.invoke(3L)).thenReturn(Optional.of(member));
        when(getMemberByIdUseCase.invoke(4L)).thenReturn(Optional.of(alone));

        mockMvc.perform(get("/api/members/3").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.householdId").value(5))
            .andExpect(jsonPath("$.householdName").value("Kebede family"));
        mockMvc.perform(get("/api/members/4").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.householdId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.householdName").value(org.hamcrest.Matchers.nullValue()));
    }
}
