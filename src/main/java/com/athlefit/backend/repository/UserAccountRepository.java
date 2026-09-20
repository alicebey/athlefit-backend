package com.athlefit.backend.repository;

import com.athlefit.backend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByFirebaseUid(String firebaseUid);

    List<UserAccount> findAllByEmailIgnoreCase(String email);
}
