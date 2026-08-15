package com.fairticketing.waitingroom.web;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Event;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final WaitingRoomStreamer streamer;

    public WaitingRoomController(WaitingRoomService waitingRoom,
                                 EventRepository events,
                                 WaitingRoomStreamer streamer) {
        this.waitingRoom = waitingRoom;
        this.events = events;
        this.streamer = streamer;
    }

    @PostMapping("/{eventId}/join")
    public WaitingRoomResponse join(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        Event event = requireEvent(eventId);
        if (!waitingRoom.enabled() || !event.isWaitingRoomEnabled()) {
            return openDoor(eventId);
        }
        return WaitingRoomResponse.from(eventId, waitingRoom.join(eventId, userId(jwt)));
    }

    /**
     * Clients poll this. Each call is also what moves the line along, so a room
     * nobody is watching costs nothing. Prefer {@link #stream} in the UI.
     */
    @GetMapping("/{eventId}")
    public WaitingRoomResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        Event event = requireEvent(eventId);
        if (!waitingRoom.enabled() || !event.isWaitingRoomEnabled()) {
            return openDoor(eventId);
        }
        return WaitingRoomResponse.from(eventId, waitingRoom.status(eventId, userId(jwt)));
    }

    /**
     * Same payload as {@link #status}, pushed until the buyer is admitted or
     * drops out. The server tick is what drains the queue for this connection.
     */
    @GetMapping(path = "/{eventId}/stream", produces = "text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        Event event = requireEvent(eventId);
        boolean queueOn = waitingRoom.enabled() && event.isWaitingRoomEnabled();
        return streamer.open(eventId, userId(jwt), queueOn);
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
    static WaitingRoomResponse openDoor(Long eventId) {
        return new WaitingRoomResponse(eventId, WaitingRoomStatus.ADMITTED, 0, 0, Duration.ZERO.toSeconds(), null);
    }

    private Event requireEvent(Long eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found"));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
