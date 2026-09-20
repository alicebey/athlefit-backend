package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.UpdateUserRequest;
import com.athlefit.backend.dto.response.UserResponse;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.repository.UserAccountRepository;
import com.athlefit.backend.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccount getOrCreate(AuthenticatedUser identity) {
        return userAccountRepository.findByFirebaseUid(identity.uid())
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        identity.uid(),
                        fallbackEmail(identity),
                        fallbackName(identity)
                )));
    }

    @Transactional
    public UserResponse findCurrent(AuthenticatedUser identity) {
        return UserResponse.from(getOrCreate(identity));
    }

    @Transactional
    public UserResponse update(AuthenticatedUser identity, UpdateUserRequest request) {
        UserAccount user = getOrCreate(identity);
        String fullName = request.fullName() == null
                ? user.getFullName()
                : requireText(request.fullName(), "fullName");
        String phone = request.phone() == null ? user.getPhone() : normalizeOptional(request.phone());
        String favoriteSport = request.favoriteSport() == null
                ? user.getFavoriteSport()
                : normalizeOptional(request.favoriteSport());
        user.updateProfile(fullName, phone, favoriteSport);
        return UserResponse.from(user);
    }

    private String fallbackEmail(AuthenticatedUser identity) {
        return identity.email() == null || identity.email().isBlank()
                ? identity.uid() + "@firebase.invalid"
                : identity.email();
    }

    private String fallbackName(AuthenticatedUser identity) {
        if (identity.name() != null && !identity.name().isBlank()) {
            return identity.name().trim();
        }
        if (identity.email() != null && identity.email().contains("@")) {
            return identity.email().substring(0, identity.email().indexOf('@'));
        }
        return "Athlefit User";
    }

    private String requireText(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
