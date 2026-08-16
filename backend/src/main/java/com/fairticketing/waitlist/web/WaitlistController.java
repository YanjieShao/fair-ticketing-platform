package com.fairticketing.waitlist.web;

import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.repository.WaitlistShowView;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.repository.WaitlistEntryRepository;
import com.fairticketing.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlist;
    private final WaitlistEntryRepository entries;
    private final TicketTierRepository tiers;

    public WaitlistController(WaitlistService waitlist,
                              WaitlistEntryRepository entries,
                              TicketTierRepository tiers) {
        this.waitlist = waitlist;
        this.entries = entries;
        this.tiers = tiers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistResponse join(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody JoinRequest request) {
        var entry = waitlist.join(userId(jwt), request.tierId(), request.quantity());
        return toResponse(entry);
    }

    @GetMapping
    public Page<WaitlistResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Page<WaitlistEntry> mine = entries.findByUserIdOrderByCreatedAtDesc(
                userId(jwt), PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)));
        Map<Long, WaitlistShowView> shows = showsByTier(mine.getContent().stream()
                .map(WaitlistEntry::getTierId)
                .collect(Collectors.toSet()));
        return mine.map(entry -> WaitlistResponse.from(
                entry, waitlist.peopleAhead(entry), shows.get(entry.getTierId())));
    }

    @GetMapping("/{entryId}")
    public WaitlistResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long entryId) {
        return toResponse(waitlist.findOwned(userId(jwt), entryId));
    }

    @DeleteMapping("/{entryId}")
    public WaitlistResponse leave(@AuthenticationPrincipal Jwt jwt, @PathVariable Long entryId) {
        var entry = waitlist.leave(userId(jwt), entryId);
        return WaitlistResponse.from(entry, 0, show(entry.getTierId()));
    }

    private WaitlistResponse toResponse(WaitlistEntry entry) {
        return WaitlistResponse.from(entry, waitlist.peopleAhead(entry), show(entry.getTierId()));
    }

    private WaitlistShowView show(Long tierId) {
        return showsByTier(List.of(tierId)).get(tierId);
    }

    private Map<Long, WaitlistShowView> showsByTier(Collection<Long> tierIds) {
        if (tierIds.isEmpty()) {
            return Map.of();
        }
        return tiers.showViewsByTierIds(tierIds).stream()
                .collect(Collectors.toMap(WaitlistShowView::tierId, Function.identity()));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record JoinRequest(@NotNull Long tierId, @Min(1) int quantity) {
    }
}
