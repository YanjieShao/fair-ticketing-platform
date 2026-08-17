package com.fairticketing.common.error;

import com.fairticketing.common.domain.IllegalStateTransitionException;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final TestHandler handler = new TestHandler(Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));

    @Test
    void business_failures_keep_their_code_and_status() {
        var response = handler.handleBusiness(new BusinessException(ErrorCode.SOLD_OUT, "gone"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("SOLD_OUT");
        assertThat(response.getBody().timestamp()).isEqualTo(Fixtures.NOW);
    }

    @Test
    void illegal_transitions_are_conflicts_not_server_errors() {
        var response = handler.handleTransition(
                new IllegalStateTransitionException("Order", OrderStatus.EXPIRED, OrderStatus.PAID));
        assertThat(response.getBody().code()).isEqualTo("ILLEGAL_STATE_TRANSITION");
    }

    @Test
    void unexpected_failures_do_not_leak_the_exception_text() {
        var response = handler.handleUnexpected(new RuntimeException("secret"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Something went wrong");
    }

    @Test
    void validation_errors_name_the_fields() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(MethodParameter.class), binding);
        ResponseEntity<Object> response = handler.exposeValid(ex);
        assertThat(response.getBody()).extracting("code").isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody()).extracting("message").asString().contains("email");
    }

    @Test
    void spring_mvc_statuses_map_onto_the_same_error_codes() {
        assertThat(handler.exposeInternal(new RuntimeException("missing"), HttpStatus.NOT_FOUND)
                .getBody()).extracting("code").isEqualTo("NOT_FOUND");
        assertThat(handler.exposeInternal(new RuntimeException("auth"), HttpStatus.UNAUTHORIZED)
                .getBody()).extracting("code").isEqualTo("UNAUTHORIZED");
        assertThat(handler.exposeInternal(new RuntimeException("no"), HttpStatus.FORBIDDEN)
                .getBody()).extracting("code").isEqualTo("FORBIDDEN");
        assertThat(handler.exposeInternal(new RuntimeException("slow"), HttpStatus.TOO_MANY_REQUESTS)
                .getBody()).extracting("code").isEqualTo("RATE_LIMITED");
        assertThat(handler.exposeInternal(new RuntimeException("bad"), HttpStatus.BAD_REQUEST)
                .getBody()).extracting("code").isEqualTo("VALIDATION_FAILED");
        assertThat(handler.exposeInternal(new RuntimeException("boom"), HttpStatus.INTERNAL_SERVER_ERROR)
                .getBody()).extracting("code").isEqualTo("INTERNAL_ERROR");
    }

    private static final class TestHandler extends GlobalExceptionHandler {
        TestHandler(Clock clock) {
            super(clock);
        }

        ResponseEntity<Object> exposeValid(MethodArgumentNotValidException ex) {
            return handleMethodArgumentNotValid(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));
        }

        ResponseEntity<Object> exposeInternal(Exception ex, HttpStatus status) {
            return handleExceptionInternal(ex, null, new HttpHeaders(), status, mock(WebRequest.class));
        }
    }
}
