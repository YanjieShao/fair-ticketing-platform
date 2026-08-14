package com.fairticketing.order.web;

import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final TicketOrderRepository orderRepository;

    public OrderController(OrderService orders, TicketOrderRepository orderRepository) {
        this.orders = orders;
        this.orderRepository = orderRepository;
    }

    /**
     * The client supplies the idempotency key, because only the client knows
     * whether a request is new or the same one being retried after a timeout.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal Jwt jwt,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @Valid @RequestBody CheckoutRequest request) {
        return OrderResponse.from(
                orders.checkout(userId(jwt), request.tierId(), request.quantity(), idempotencyKey));
    }

    @PostMapping("/{orderNo}/pay")
    public OrderResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNo) {
        return OrderResponse.from(orders.pay(userId(jwt), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNo) {
        return OrderResponse.from(orders.cancel(userId(jwt), orderNo));
    }

    @GetMapping("/{orderNo}")
    public OrderResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNo) {
        return OrderResponse.from(orders.findOwned(userId(jwt), orderNo));
    }

    @GetMapping
    public Page<OrderResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId(jwt), PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)))
                .map(OrderResponse::from);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record CheckoutRequest(@NotNull Long tierId, @Min(1) int quantity) {
    }
}
