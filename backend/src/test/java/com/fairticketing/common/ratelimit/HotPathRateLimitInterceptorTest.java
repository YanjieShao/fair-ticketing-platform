package com.fairticketing.common.ratelimit;

import com.fairticketing.common.ratelimit.AccountRateLimiter.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HotPathRateLimitInterceptorTest {

    private final AccountRateLimiter limiter = mock(AccountRateLimiter.class);
    private final HotPathRateLimitInterceptor interceptor = new HotPathRateLimitInterceptor(limiter);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_requests_are_not_capped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter, never()).guard(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkout_and_join_paths_are_the_ones_a_script_would_hammer() {
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/orders")).isEqualTo(Action.CHECKOUT);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/waitlist")).isEqualTo(Action.JOIN);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/waiting-room/5/join")).isEqualTo(Action.JOIN);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/events")).isNull();
    }

    @Test
    void an_authenticated_checkout_is_guarded() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("7").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setServletPath("/api/orders");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter).guard(7L, Action.CHECKOUT);
    }

    @Test
    void anonymous_posts_are_left_to_the_security_filter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setServletPath("/api/orders");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter, never()).guard(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pathOf_falls_back_to_the_request_uri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("");
        request.setRequestURI("/api/waitlist");
        assertThat(HotPathRateLimitInterceptor.pathOf(request)).isEqualTo("/api/waitlist");
    }
}
