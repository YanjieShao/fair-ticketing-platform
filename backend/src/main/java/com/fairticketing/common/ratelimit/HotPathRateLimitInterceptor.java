package com.fairticketing.common.ratelimit;

import com.fairticketing.common.ratelimit.AccountRateLimiter.Action;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies {@link AccountRateLimiter} to the write paths a script would hammer.
 * GET polling (waiting-room status, order lists) is left alone so a person
 * sitting in line is not punished for staying on the page.
 */
@Component
public class HotPathRateLimitInterceptor implements HandlerInterceptor {

    private final AccountRateLimiter limiter;

    public HotPathRateLimitInterceptor(AccountRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Action action = actionFor(pathOf(request));
        if (action == null) {
            return true;
        }
        Long userId = currentUserId();
        if (userId == null) {
            return true;
        }
        limiter.guard(userId, action);
        return true;
    }

    static Action actionFor(String path) {
        if ("/api/orders".equals(path)) {
            return Action.CHECKOUT;
        }
        if ("/api/waitlist".equals(path) || path.matches("/api/waiting-room/\\d+/join")) {
            return Action.JOIN;
        }
        return null;
    }

    static String pathOf(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        return uri == null ? "" : uri;
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String subject = principal instanceof Jwt jwt
                ? jwt.getSubject()
                : authentication instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : null;
        if (subject == null) {
            return null;
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
