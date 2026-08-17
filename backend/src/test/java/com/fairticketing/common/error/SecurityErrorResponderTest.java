package com.fairticketing.common.error;

import com.fairticketing.support.Fixtures;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityErrorResponderTest {

    private final SecurityErrorResponder responder =
            new SecurityErrorResponder(Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));

    @Test
    void unauthenticated_calls_use_the_same_error_shape_as_the_rest_of_the_api() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        responder.commence(mock(HttpServletRequest.class), response, new BadCredentialsException("no"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getContentAsString()).contains(Fixtures.NOW.toString());
    }

    @Test
    void forbidden_calls_do_too() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        responder.handle(mock(HttpServletRequest.class), response, new AccessDeniedException("no"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
