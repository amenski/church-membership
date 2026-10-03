package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetryDeliveryUseCaseTest {

    private static final Long COMMUNICATION_ID = 7L;
    private static final Long DELIVERY_ID = 11L;

    private CommunicationRepository communicationRepository;
    private MessageDeliveryRepository deliveryRepository;
    private EmailService emailService;
    private RetryDeliveryUseCase useCase;

    private Member member;
    private Communication communication;
    private MessageDelivery delivery;

    @BeforeEach
    void setUp() {
        communicationRepository = mock(CommunicationRepository.class);
        deliveryRepository = mock(MessageDeliveryRepository.class);
        emailService = mock(EmailService.class);
        useCase = new RetryDeliveryUseCase(communicationRepository, deliveryRepository, emailService);

        member = new Member();
        member.setId(1L);
        member.setEmail("a@example.com");

        communication = new Communication();
        communication.setId(COMMUNICATION_ID);
        communication.setTitle("Title");
        communication.setMessageContent("Body");

        Communication minimal = new Communication();
        minimal.setId(COMMUNICATION_ID);
        delivery = new MessageDelivery(member, minimal, MessageDelivery.DeliveryChannel.EMAIL);
        delivery.setId(DELIVERY_ID);
        delivery.setStatus(MessageDelivery.DeliveryStatus.FAILED);

        when(deliveryRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(delivery));
        when(communicationRepository.findById(COMMUNICATION_ID)).thenReturn(Optional.of(communication));
        when(deliveryRepository.save(any(MessageDelivery.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void failedDeliveryIsResentAndMarkedSent() {
        when(emailService.sendSimpleEmailWithRetry(member, "Title", "Body", null)).thenReturn(true);

        LocalDateTime before = LocalDateTime.now();
        MessageDelivery result = useCase.invoke(COMMUNICATION_ID, DELIVERY_ID);

        assertThat(result.getStatus()).isEqualTo(MessageDelivery.DeliveryStatus.SENT);
        assertThat(result.getDeliveryTime()).isAfterOrEqualTo(before);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void failedAgainStaysFailedWithNotes() {
        when(emailService.sendSimpleEmailWithRetry(member, "Title", "Body", null)).thenReturn(false);

        MessageDelivery result = useCase.invoke(COMMUNICATION_ID, DELIVERY_ID);

        assertThat(result.getStatus()).isEqualTo(MessageDelivery.DeliveryStatus.FAILED);
        assertThat(result.getResponseNotes()).startsWith("Retry failed at ");
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void nonFailedDeliveryIsRejectedAndNothingSent() {
        delivery.setStatus(MessageDelivery.DeliveryStatus.SENT);

        assertThatThrownBy(() -> useCase.invoke(COMMUNICATION_ID, DELIVERY_ID))
                .isInstanceOf(CommunicationDomainException.class);

        verifyNoInteractions(emailService);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void deliveryOfAnotherCommunicationIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(999L, DELIVERY_ID))
                .isInstanceOf(CommunicationDomainException.class);

        verifyNoInteractions(emailService);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void nonEmailChannelIsRejected() {
        delivery.setChannel(MessageDelivery.DeliveryChannel.SMS);

        assertThatThrownBy(() -> useCase.invoke(COMMUNICATION_ID, DELIVERY_ID))
                .isInstanceOf(CommunicationDomainException.class);

        verifyNoInteractions(emailService);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void unknownDeliveryIsRejected() {
        when(deliveryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke(COMMUNICATION_ID, 404L))
                .isInstanceOf(CommunicationDomainException.class);

        verifyNoInteractions(emailService);
        verify(deliveryRepository, never()).save(any());
    }
}
