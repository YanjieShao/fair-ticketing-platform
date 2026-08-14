package com.fairticketing.common.error;

import com.fairticketing.common.domain.IllegalStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Every failure leaves through here in the same shape, so a client needs to
 * understand one error format rather than three.
 *
 * <p>Extending Spring's handler matters: it already knows the correct status for
 * a missing header, an unreadable body, or a mistyped path variable. Left to a
 * catch-all, those become 500s, which tells the caller their request was fine
 * and wakes someone up for a fault that does not exist.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return respond(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiError> handleTransition(IllegalStateTransitionException ex) {
        return respond(ErrorCode.ILLEGAL_STATE_TRANSITION, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Something went wrong");
    }

    /** Names the offending fields instead of returning Spring's stringified exception. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ResponseEntity<>(body(ErrorCode.VALIDATION_FAILED, detail), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object ignoredBody,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        return new ResponseEntity<>(body(codeFor(status), ex.getMessage()), headers, status);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        };
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(body(code, message));
    }

    private ApiError body(ErrorCode code, String message) {
        return new ApiError(code.name(), message, Instant.now(clock));
    }

    public record ApiError(String code, String message, Instant timestamp) {
    }
}
