package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.Payment;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An ARCHIVED member is visible only to an ADMIN on every read path: by id, in the id-based export, in a
 * member's payments, as the recipient of a message, and embedded in payments and deliveries (contact details blanked).
 */
@WebMvcTest(controllers = {MemberController.class, PaymentController.class, CommunicationController.class,
    DashboardController.class, ActivityLogController.class})
@Import({SecurityConfig.class, AuthProperties.class})
class ArchivedVisibilityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private RecordActivityUseCase recordActivityUseCase;
    @MockitoBean private GetActivityLogUseCase getActivityLogUseCase;
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
    @MockitoBean private GetAllPaymentsUseCase getAllPaymentsUseCase;
    @MockitoBean private GetPaymentByIdUseCase getPaymentByIdUseCase;
    @MockitoBean private GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    @MockitoBean private RecordPaymentUseCase recordPaymentUseCase;
    @MockitoBean private GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    @MockitoBean private GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    @MockitoBean private SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    @MockitoBean private SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    @MockitoBean private GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase;
    @MockitoBean private RetryDeliveryUseCase retryDeliveryUseCase;
    @MockitoBean private GetDashboardStatsUseCase getDashboardStatsUseCase;
    @MockitoBean private GetRecentPaymentsUseCase getRecentPaymentsUseCase;
    @MockitoBean private GetRecentCommunicationsUseCase getRecentCommunicationsUseCase;

    private Member archived;

    private static RequestPostProcessor as(String role) {
        return user("tester@example.com").roles(role);
    }

    @BeforeEach
    void archivedMember() {
        archived = new Member("Old Friend", "old@example.com", "+390612345678");
        archived.setId(9L);
        archived.setStatus(MemberStatus.ARCHIVED);
        when(getMemberByIdUseCase.invoke(9L)).thenReturn(Optional.of(archived));
        when(getMemberByIdUseCase.invoke(404L)).thenReturn(Optional.empty());
    }

    @Test
    void anArchivedMemberIsNotFoundByIdForStaffAndVolunteer() throws Exception {
        for (String role : List.of("VOLUNTEER", "STAFF")) {
            mockMvc.perform(get("/api/members/9").with(as(role))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/members/404").with(as("STAFF"))).andExpect(status().isNotFound());
    }

    @Test
    void anAdminCanReadAnArchivedMemberById() throws Exception {
        mockMvc.perform(get("/api/members/9").with(as("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("old@example.com"));
    }

    @Test
    void theIdBasedExportDropsArchivedMembersForStaffButNotForAdmin() throws Exception {
        Member live = new Member("Live", "live@example.com", "+390612345678");
        live.setId(1L);
        when(getMemberByIdUseCase.invoke(1L)).thenReturn(Optional.of(live));
        String body = "{\"ids\":[1,9]}";

        String staff = mockMvc.perform(post("/api/members/export").with(csrf()).with(as("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String admin = mockMvc.perform(post("/api/members/export").with(csrf()).with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(staff).contains("Live").doesNotContain("Old Friend").doesNotContain("old@example.com");
        org.assertj.core.api.Assertions.assertThat(admin).contains("Live").contains("Old Friend");
    }

    @Test
    void paymentsOfAnArchivedMemberAreNotFoundForStaffAndListedForAdmin() throws Exception {
        when(getPaymentsByMemberUseCase.invoke(archived)).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/member/9").with(as("STAFF"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/member/9").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void sendingToAnArchivedMemberIsMemberNotFoundForStaff() throws Exception {
        mockMvc.perform(post("/api/communications/send-to-member/9").with(csrf()).with(as("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hi\",\"messageContent\":\"Hello\",\"type\":\"PERSONAL\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MEMBER_006"));
    }

    @Test
    void anArchivedRecipientKeepsTheNameButLosesTheContactDetailsForStaff() throws Exception {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setRecipient(archived);
        when(getDeliveriesByCommunicationUseCase.invoke(5L)).thenReturn(List.of(delivery));

        mockMvc.perform(get("/api/communications/5/deliveries").with(as("STAFF")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].recipient.name").value("Old Friend"))
            .andExpect(jsonPath("$[0].recipient.email").value(nullValue()))
            .andExpect(jsonPath("$[0].recipient.phone").value(nullValue()));
    }

    @Test
    void anArchivedMemberInTheAllPaymentsListLosesTheContactDetailsForStaffButNotForAdmin() throws Exception {
        Payment payment = new Payment();
        payment.setMember(archived);
        when(getAllPaymentsUseCase.invoke()).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/payments").with(as("STAFF")))
            .andExpect(jsonPath("$[0].member.name").value("Old Friend"))
            .andExpect(jsonPath("$[0].member.email").value(nullValue()));
        archived.setEmail("old@example.com");
        mockMvc.perform(get("/api/payments").with(as("ADMIN")))
            .andExpect(jsonPath("$[0].member.email").value("old@example.com"));
    }
}
