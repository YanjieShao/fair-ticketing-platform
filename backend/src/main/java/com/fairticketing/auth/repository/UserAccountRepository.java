package com.fairticketing.auth.repository;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserAccount> findByRole(UserRole role);
}
