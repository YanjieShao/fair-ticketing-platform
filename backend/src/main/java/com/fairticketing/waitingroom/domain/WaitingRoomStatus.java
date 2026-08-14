package com.fairticketing.waitingroom.domain;

/**
 * What the queue currently thinks of one buyer. The numeric codes are the
 * contract with {@code redis/waiting_room.lua}, which can only return numbers.
 */
public enum WaitingRoomStatus {

    /** Never joined, or the pass expired and the line moved on without them. */
    NOT_QUEUED,

    /** In line. The response carries the position so the client can show it. */
    WAITING,

    /** Allowed to buy until the admission expires. */
    ADMITTED;

    public static WaitingRoomStatus ofCode(long code) {
        return switch ((int) code) {
            case 1 -> WAITING;
            case 2 -> ADMITTED;
            default -> NOT_QUEUED;
        };
    }
}
