package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import io.github.membertracker.utils.MessageTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryDeliveryUseCase {

    private static final Logger logger = LoggerFactory.getLogger(RetryDeliveryUseCase.class);

    private final CommunicationRepository communicationRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final EmailService emailService;

    public RetryDeliveryUseCase(CommunicationRepository communicationRepository,
                                MessageDeliveryRepository messageDeliveryRepository,
                                EmailService emailService) {
        this.communicationRepository = communicationRepository;
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.emailService = emailService;
    }

    /**
     * Re-sends one FAILED email delivery, synchronously and once; the attempts it made are added to the stored count.
     *
     * @param communicationId the communication the delivery must belong to
     * @param deliveryId      the delivery to retry
     * @return the saved delivery (SENT on success, still FAILED otherwise)
     */
    public MessageDelivery invoke(Long communicationId, Long deliveryId) {
        MessageDelivery delivery = messageDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> CommunicationDomainException.deliveryNotFound(deliveryId));

        if (delivery.getCommunication() == null
                || !communicationId.equals(delivery.getCommunication().getId())) {
            throw CommunicationDomainException.deliveryCommunicationMismatch(deliveryId, communicationId);
        }
        if (delivery.getStatus() != MessageDelivery.DeliveryStatus.FAILED) {
            throw CommunicationDomainException.deliveryNotRetryable("only FAILED deliveries can be retried");
        }
        if (delivery.getChannel() != MessageDelivery.DeliveryChannel.EMAIL) {
            throw CommunicationDomainException.deliveryNotRetryable("only EMAIL deliveries can be retried");
        }

        if (delivery.getRecipient() == null || delivery.getRecipient().getEmail() == null
                || delivery.getRecipient().getEmail().isBlank()) {
            throw CommunicationDomainException.deliveryNotRetryable("the member no longer has an email address");
        }

        // The persisted delivery only carries the communication id, so load the full one for title/content
        Communication communication = communicationRepository.findById(communicationId)
                .orElseThrow(() -> CommunicationDomainException.communicationNotFound(communicationId));

        AtomicInteger attempts = new AtomicInteger();
        boolean sent = emailService.sendSimpleEmailWithRetry(
                delivery.getRecipient(),
                MessageTemplates.personalize(communication.getTitle(), delivery.getRecipient()),
                MessageTemplates.personalize(communication.getMessageContent(), delivery.getRecipient()),
                (currentAttempt, maxAttempts) -> attempts.set(currentAttempt)
        );
        delivery.setAttempts(delivery.getAttempts() + attempts.get());

        if (sent) {
            delivery.setStatus(MessageDelivery.DeliveryStatus.SENT);
            delivery.setDeliveryTime(LocalDateTime.now());
            logger.info("Retry of delivery {} succeeded", deliveryId);
        } else {
            delivery.setResponseNotes("Retry failed at "
                    + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logger.warn("Retry of delivery {} failed", deliveryId);
        }
        return messageDeliveryRepository.save(delivery);
    }
}
