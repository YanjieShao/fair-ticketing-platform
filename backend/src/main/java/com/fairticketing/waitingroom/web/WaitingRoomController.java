package com.fairticketing.waitingroom.web;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Deliberately not nested under {@code /api/events}, where GET is public: the
 * queue is keyed by user, so every one of these needs a token.
 */
@RestController
@RequestMapping("/api/waiting-room")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoom;
    private final EventRepository events;

    public WaitingRoomController(WaitingRoomService waitingRoom, EventRepository events) {
        this.waitingRoom = waitingRoom;
        this.events = events;
    }

    @PostMapping("/{eventId}/join")
    public WaitingRoomResponse join(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        requireEvent(eventId);
        if (!waitingRoom.enabled()) {
            return openDoor(eventId);
        }
        return WaitingRoomResponse.from(eventId, waitingRoom.join(eventId, userId(jwt)));
    }

    /**
     * Clients poll this. Each call is also what moves the line along, so a room
     * nobody is watching costs nothing.
     */
    @GetMapping("/{eventId}")
    public WaitingRoomResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        requireEvent(eventId);
        if (!waitingRoom.enabled()) {
            return openDoor(eventId);
        }
        return WaitingRoomResponse.from(eventId, waitingRoom.status(eventId, userId(jwt)));
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        waitingRoom.leave(eventId, userId(jwt));
    }

    /**
     * With the queue switched off the answer is still a valid pass, so clients
     * do not need a second code path for deployments that do not need one.
     */
    private static WaitingRoomResponse openDoor(Long eventId) {
        return new WaitingRoomResponse(eventId, WaitingRoomStatus.ADMITTED, 0, 0, Duration.ZERO.toSeconds(), null);
    }

    private void requireEvent(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found");
        }
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
