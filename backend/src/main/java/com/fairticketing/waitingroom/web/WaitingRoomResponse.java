package com.fairticketing.waitingroom.web;

import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import com.fairticketing.waitingroom.service.WaitingRoomService;

import java.time.Instant;

/**
 * @param position            1-based, 0 once admitted
 * @param estimatedWaitSeconds an estimate, not a promise: it assumes nobody
 *                             ahead gives up and the admit rate does not change
 */
public record WaitingRoomResponse(Long eventId,
                                  WaitingRoomStatus status,
                                  long position,
                                  long queueLength,
                                  long estimatedWaitSeconds,
                                  Instant admissionExpiresAt) {

    public static WaitingRoomResponse from(Long eventId, WaitingRoomService.Pass pass) {
        return new WaitingRoomResponse(
                eventId,
                pass.status(),
                pass.position(),
                pass.queueLength(),
                pass.estimatedWait().toSeconds(),
                pass.admissionExpiresAt());
    }
}
