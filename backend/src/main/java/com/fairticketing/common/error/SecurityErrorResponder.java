package com.fairticketing.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * Authentication failures are rejected by filters that run before any
 * controller, so they never reach the exception handler and would otherwise
 * come back as an empty body. A client that has to special-case two different
 * error shapes will get one of them wrong.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final Clock clock;

    public SecurityErrorResponder(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, ErrorCode.UNAUTHORIZED, "Authentication is required for this endpoint");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, ErrorCode.FORBIDDEN, "You do not have access to this endpoint");
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"%s","message":"%s","timestamp":"%s"}""".formatted(code.name(), message, Instant.now(clock)));
    }
}
