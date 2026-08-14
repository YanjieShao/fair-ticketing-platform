package com.fairticketing.common.domain;

/**
 * Raised when a domain object is asked to move between two states that the
 * state machine does not connect.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super("%s cannot move from %s to %s".formatted(entity, from, to));
    }
}
