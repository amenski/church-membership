package io.github.membertracker.domain.exception;

public abstract class DomainException extends RuntimeException {
    
    private final String errorCode;
    private final String entityType;
    private final String field;

    protected DomainException(String message, String errorCode, String entityType) {
        this(message, errorCode, entityType, (String) null);
    }

    /** For a rule broken by one request field: the API reports it as a field error, like Bean Validation does. */
    protected DomainException(String message, String errorCode, String entityType, String field) {
        super(message);
        this.errorCode = errorCode;
        this.entityType = entityType;
        this.field = field;
    }

    protected DomainException(String message, String errorCode, String entityType, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.entityType = entityType;
        this.field = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getEntityType() {
        return entityType;
    }

    /** The request field this violation is about, or null. */
    public String getField() {
        return field;
    }

    public String getUserMessage() {
        return getMessage();
    }
}