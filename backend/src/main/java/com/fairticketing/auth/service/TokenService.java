package com.fairticketing.auth.service;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.common.config.TicketingProperties;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final TicketingProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, TicketingProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.security().accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("fair-ticketing")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();

        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(() -> "HS256").build(), claims))
                .getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
