package io.github.membertracker.domain.exception;

public abstract class DomainException extends RuntimeException {
    
    private final String errorCode;
    private final String entityType;

    protected DomainException(String message, String errorCode, String entityType) {
        super(message);
        this.errorCode = errorCode;
        this.entityType = entityType;
    }

    protected DomainException(String message, String errorCode, String entityType, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.entityType = entityType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getUserMessage() {
        return getMessage();
    }
}