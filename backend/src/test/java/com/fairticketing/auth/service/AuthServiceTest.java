package com.fairticketing.auth.service;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtEncoder encoder = mock(JwtEncoder.class);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
        TokenService tokens = new TokenService(encoder, Fixtures.properties(), clock);
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("issued.jwt").header("alg", "none").subject("1").build());
        auth = new AuthService(users, passwords, tokens, clock);
    }

    @Test
    void register_rejects_a_duplicate_email() {
        when(users.existsByEmail("a@b.c")).thenReturn(true);
        assertThatThrownBy(() -> auth.register("a@b.c", "password1", "Ada"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void register_issues_a_token_for_a_new_buyer() {
        when(users.existsByEmail("a@b.c")).thenReturn(false);
        when(passwords.encode("password1")).thenReturn("hash");
        when(users.save(any(UserAccount.class))).thenAnswer(call -> {
            UserAccount user = call.getArgument(0);
            user.setId(11L);
            return user;
        });

        TokenService.IssuedToken token = auth.register("a@b.c", "password1", "Ada");
        assertThat(token.value()).isEqualTo("issued.jwt");
        assertThat(token.expiresAt()).isEqualTo(Fixtures.NOW.plusSeconds(7200));
    }

    @Test
    void login_rejects_an_unknown_email() {
        when(users.findByEmail("a@b.c")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.login("a@b.c", "password1"))
                .extracting("code").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_rejects_a_wrong_password() {
        UserAccount user = new UserAccount("a@b.c", "hash", "Ada", UserRole.USER, Fixtures.NOW);
        user.setId(11L);
        when(users.findByEmail("a@b.c")).thenReturn(Optional.of(user));
        when(passwords.matches("nope", "hash")).thenReturn(false);
        assertThatThrownBy(() -> auth.login("a@b.c", "nope"))
                .extracting("code").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_issues_a_token_when_the_password_matches() {
        UserAccount user = new UserAccount("a@b.c", "hash", "Ada", UserRole.USER, Fixtures.NOW);
        user.setId(11L);
        when(users.findByEmail("a@b.c")).thenReturn(Optional.of(user));
        when(passwords.matches("password1", "hash")).thenReturn(true);
        assertThat(auth.login("a@b.c", "password1").value()).isEqualTo("issued.jwt");
    }
}
