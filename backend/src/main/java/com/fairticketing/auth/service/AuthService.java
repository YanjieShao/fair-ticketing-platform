package com.fairticketing.auth.service;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;

    public AuthService(UserAccountRepository users,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public TokenService.IssuedToken register(String email, String rawPassword, String displayName) {
        if (users.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "That email is already registered");
        }
        UserAccount user = new UserAccount(
                email,
                passwordEncoder.encode(rawPassword),
                displayName,
                UserRole.USER,
                Instant.now(clock));
        return tokenService.issue(users.save(user));
    }

    @Transactional(readOnly = true)
    public TokenService.IssuedToken login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email or password is wrong"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email or password is wrong");
        }
        return tokenService.issue(user);
    }
}
