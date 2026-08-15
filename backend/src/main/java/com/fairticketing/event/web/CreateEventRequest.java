package com.fairticketing.event.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String category,
        @NotBlank String artistName,
        @NotBlank String genre,
        @Min(0) @Max(100) int popularityScore,
        @NotBlank String venueName,
        @NotBlank String city,
        @NotBlank String country,
        @Min(1) int capacity,
        @NotBlank String timezone,
        @NotNull Instant startsAt,
        @NotNull Instant salesStartAt,
        @NotNull Instant salesEndAt,
        boolean waitingRoomEnabled,
        @NotEmpty @Size(max = 5) List<@Valid TierRequest> tiers) {

    public record TierRequest(
            @NotBlank String name,
            @Min(1) int priceCents,
            @Min(1) int totalQuantity,
            @Min(1) @Max(4) int maxPerUser) {
    }
}
