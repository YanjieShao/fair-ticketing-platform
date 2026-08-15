package com.fairticketing.waitingroom.web;

import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

/**
 * Pushes queue position over SSE. Each tick still calls {@link WaitingRoomService#status},
 * which is what drains the line; the browser no longer has to poll for that
 * to happen.
 */
@Component
public class WaitingRoomStreamer {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomStreamer.class);
    private static final Duration KEEP_OPEN = Duration.ofMinutes(20);
    private static final Duration TICK = Duration.ofSeconds(1);

    private final WaitingRoomService waitingRoom;

    public WaitingRoomStreamer(WaitingRoomService waitingRoom) {
        this.waitingRoom = waitingRoom;
    }

    public SseEmitter open(Long eventId, Long userId, boolean queueOn) {
        SseEmitter emitter = new SseEmitter(KEEP_OPEN.toMillis());
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.debug("Waiting-room stream closed for event {}: {}", eventId, error.toString()));
        Thread.startVirtualThread(() -> push(emitter, eventId, userId, queueOn));
        return emitter;
    }

    private void push(SseEmitter emitter, Long eventId, Long userId, boolean queueOn) {
        try {
            while (true) {
                WaitingRoomResponse snapshot = snapshot(eventId, userId, queueOn);
                emitter.send(SseEmitter.event().name("queue").data(snapshot, MediaType.APPLICATION_JSON));
                if (snapshot.status() != WaitingRoomStatus.WAITING) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(TICK);
            }
        } catch (IOException closed) {
            emitter.complete();
        } catch (InterruptedException interrupted) {
            emitter.complete();
            Thread.currentThread().interrupt();
        } catch (RuntimeException failed) {
            emitter.completeWithError(failed);
        }
    }

    private WaitingRoomResponse snapshot(Long eventId, Long userId, boolean queueOn) {
        if (!queueOn) {
            return WaitingRoomController.openDoor(eventId);
        }
        return WaitingRoomResponse.from(eventId, waitingRoom.status(eventId, userId));
    }
}
