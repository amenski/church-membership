package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The send-communication fixture is shared with the frontend test
 * (frontend/src/__tests__/utils/communicationPayload.test.js): both sides must agree on the body shape.
 */
@WebMvcTest(controllers = CommunicationController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class CommunicationContractTest {

    private static final String FIXTURE_PATH = "src/test/resources/contracts/send-communication-request.json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    @MockitoBean private GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private CreateCommunicationUseCase createCommunicationUseCase;
    @MockitoBean private SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    @MockitoBean private SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    @MockitoBean private GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase;
    @MockitoBean private RetryDeliveryUseCase retryDeliveryUseCase;

    private static String fixture() throws Exception {
        return Files.readString(Path.of(FIXTURE_PATH));
    }

    private static void assertBound(Communication communication) {
        assertThat(communication.getTitle()).isEqualTo("Payment reminder");
        assertThat(communication.getMessageContent()).isEqualTo("Dear member, your payment is overdue.");
        assertThat(communication.getType()).isEqualTo(CommunicationType.ANNOUNCEMENT);
        assertThat(communication.getSentDate()).isNull();
    }

    @Test
    void createBindsTheFixture() throws Exception {
        when(createCommunicationUseCase.invoke(any())).thenReturn(new Communication());

        mockMvc.perform(post("/api/communications").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        ArgumentCaptor<Communication> captor = ArgumentCaptor.forClass(Communication.class);
        verify(createCommunicationUseCase).invoke(captor.capture());
        assertBound(captor.getValue());
    }

    @Test
    void sendToAllBindsTheFixture() throws Exception {
        when(sendCommunicationToAllMembersUseCase.invoke(any())).thenReturn(new Communication());

        mockMvc.perform(post("/api/communications/send-to-all").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        ArgumentCaptor<Communication> captor = ArgumentCaptor.forClass(Communication.class);
        verify(sendCommunicationToAllMembersUseCase).invoke(captor.capture());
        assertBound(captor.getValue());
    }

    @Test
    void sendToOverdueBindsTheFixture() throws Exception {
        when(getMembersWithMissedPaymentsUseCase.invoke(2)).thenReturn(List.of());
        when(sendCommunicationToMembersUseCase.invoke(any(), anyList(), any())).thenReturn(new Communication());

        mockMvc.perform(post("/api/communications/send-to-overdue/2").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        ArgumentCaptor<Communication> captor = ArgumentCaptor.forClass(Communication.class);
        verify(sendCommunicationToMembersUseCase).invoke(captor.capture(), anyList(), eq(MessageDelivery.DeliveryChannel.EMAIL));
        assertBound(captor.getValue());
    }

    @Test
    void sendToMemberBindsTheFixtureAndTargetsOneMember() throws Exception {
        when(getMemberByIdUseCase.invoke(1L)).thenReturn(Optional.of(new Member()));
        when(sendCommunicationToMembersUseCase.invoke(any(), anyList(), any())).thenReturn(new Communication());

        mockMvc.perform(post("/api/communications/send-to-member/1").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isOk());

        ArgumentCaptor<Communication> captor = ArgumentCaptor.forClass(Communication.class);
        verify(sendCommunicationToMembersUseCase).invoke(captor.capture(), anyList(), eq(MessageDelivery.DeliveryChannel.EMAIL));
        assertBound(captor.getValue());
    }

    @Test
    void clientSuppliedSentDateIsIgnored() throws Exception {
        when(createCommunicationUseCase.invoke(any())).thenReturn(new Communication());

        mockMvc.perform(post("/api/communications").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Payment reminder\", \"messageContent\": \"Dear member, your payment is overdue.\", \"sentDate\": \"2020-01-01T00:00:00\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<Communication> captor = ArgumentCaptor.forClass(Communication.class);
        verify(createCommunicationUseCase).invoke(captor.capture());
        assertBound(captor.getValue());
    }

    @Test
    void blankTitleIsRejectedWithTheFieldName() throws Exception {
        mockMvc.perform(post("/api/communications").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\", \"messageContent\": \"m\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'title')]").isNotEmpty());

        verify(createCommunicationUseCase, never()).invoke(any());
    }

    @Test
    void sendToMemberWithUnknownMemberIsRejected() throws Exception {
        when(getMemberByIdUseCase.invoke(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/communications/send-to-member/99").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture()))
            .andExpect(status().isBadRequest());

        verify(sendCommunicationToMembersUseCase, never()).invoke(any(), anyList(), any());
    }
}
