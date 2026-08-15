package com.fairticketing.auth;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The demo admin is independent of the synthetic history seeder, so a laptop
 * with an empty database can still list a show and run the UI.
 */
@Component
@Order(1)
public class EnsureAdminAccount implements ApplicationRunner {

    public static final String EMAIL = "admin@fairticketing.local";
    public static final String PASSWORD = "password123";

    private static final Logger log = LoggerFactory.getLogger(EnsureAdminAccount.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public EnsureAdminAccount(UserAccountRepository users, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(EMAIL)) {
            return;
        }
        users.save(new UserAccount(
                EMAIL, passwords.encode(PASSWORD), "Admin", UserRole.ADMIN, Instant.now(clock)));
        log.info("Created demo admin {}", EMAIL);
    }
}
