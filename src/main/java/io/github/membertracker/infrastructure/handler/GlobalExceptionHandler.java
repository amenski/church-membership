package io.github.membertracker.infrastructure.handler;

import io.github.membertracker.domain.exception.DomainException;
import io.github.membertracker.infrastructure.security.TooManyLoginAttemptsException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Every API error is an RFC 7807 {@link ProblemDetail}, built through
 * {@link ProblemDetails}. Rejected values are never echoed back, and
 * unexpected errors return a fixed generic message.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Gives problems built by Spring's base class (malformed JSON, 404, 405, ...) the same properties. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemDetails.addCommonProperties(problem, path(request));
        }
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toError)
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "One or more fields are invalid", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex, WebRequest request) {
        log.error("Business rule violation: {}", ex.getMessage());
        HttpStatus status = ex.isConflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        ProblemDetail problem = problem(status, ex.getUserMessage(), request);
        problem.setProperty("code", ex.getErrorCode());
        if (ex.getField() != null) {
            problem.setProperty("errors", List.of(Map.of("field", ex.getField(), "message", ex.getUserMessage())));
        }
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex, WebRequest request) {
        log.warn("Sign-in throttled, retry after {} s", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.error("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "The request conflicts with existing data", request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(this::toError)
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "One or more parameters are invalid", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    protected ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "Access denied", request));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    protected ResponseEntity<ProblemDetail> handleInsufficientAuthentication(
            InsufficientAuthenticationException ex, WebRequest request) {
        log.error("Insufficient authentication: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "Authentication required", request));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request));
    }

    private ProblemDetail problem(HttpStatus status, String detail, WebRequest request) {
        return ProblemDetails.of(status, detail, path(request));
    }

    private String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getRequestURI()
                : request.getDescription(false).replace("uri=", "");
    }

    private Map<String, String> toError(FieldError fieldError) {
        return Map.of("field", fieldError.getField(), "message", String.valueOf(fieldError.getDefaultMessage()));
    }

    private Map<String, String> toError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return Map.of("field", field, "message", violation.getMessage());
    }
}
