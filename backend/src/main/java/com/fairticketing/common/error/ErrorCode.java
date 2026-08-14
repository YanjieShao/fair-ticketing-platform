package com.fairticketing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Checkout failures are part of the product, not accidents: a buyer who loses a
 * race needs to be told which rule stopped them. Every code carries the status
 * the API should answer with.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    EVENT_NOT_ON_SALE(HttpStatus.CONFLICT),
    SOLD_OUT(HttpStatus.CONFLICT),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    DUPLICATE_ACTIVE_ORDER(HttpStatus.CONFLICT),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),

    WAITING_ROOM_TOKEN_REQUIRED(HttpStatus.FORBIDDEN),
    OFFER_WINDOW_CLOSED(HttpStatus.CONFLICT),
    WAITLIST_NOT_NEEDED(HttpStatus.CONFLICT),
    ALREADY_ON_WAITLIST(HttpStatus.CONFLICT),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
