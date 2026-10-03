package io.github.membertracker.domain.exception;

/**
 * Domain exception for Communication and message delivery violations.
 */
public class CommunicationDomainException extends DomainException {

    // Error codes for different types of communication domain violations
    public static final String DELIVERY_NOT_FOUND = "COMMUNICATION_001";
    public static final String DELIVERY_COMMUNICATION_MISMATCH = "COMMUNICATION_002";
    public static final String DELIVERY_NOT_RETRYABLE = "COMMUNICATION_003";
    public static final String COMMUNICATION_NOT_FOUND = "COMMUNICATION_004";
    public static final String ALREADY_SENT = "COMMUNICATION_005";
    public static final String NO_RECIPIENTS = "COMMUNICATION_006";

    public CommunicationDomainException(String message, String errorCode) {
        super(message, errorCode, "Communication");
    }

    // Factory methods for common communication domain violations
    public static CommunicationDomainException noRecipients() {
        return new CommunicationDomainException("There is nobody to send this to.", NO_RECIPIENTS);
    }

    public static CommunicationDomainException deliveryNotFound(Long deliveryId) {
        return new CommunicationDomainException(
            String.format("Delivery with ID %d not found", deliveryId),
            DELIVERY_NOT_FOUND
        );
    }

    public static CommunicationDomainException communicationNotFound(Long communicationId) {
        return new CommunicationDomainException(
            String.format("Communication with ID %d not found", communicationId),
            COMMUNICATION_NOT_FOUND
        );
    }

    public static CommunicationDomainException deliveryCommunicationMismatch(Long deliveryId, Long communicationId) {
        return new CommunicationDomainException(
            String.format("Delivery %d does not belong to communication %d", deliveryId, communicationId),
            DELIVERY_COMMUNICATION_MISMATCH
        );
    }

    public static CommunicationDomainException deliveryNotRetryable(String reason) {
        return new CommunicationDomainException(
            String.format("Delivery cannot be retried: %s", reason),
            DELIVERY_NOT_RETRYABLE
        );
    }
}
