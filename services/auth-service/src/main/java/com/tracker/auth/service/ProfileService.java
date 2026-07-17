package com.tracker.auth.service;

import com.tracker.auth.domain.UserAccount;
import com.tracker.auth.dto.AuthDtos.ProfileResponse;
import com.tracker.auth.dto.AuthDtos.UpdateProfileRequest;
import com.tracker.auth.exception.InvalidCredentialsException;
import com.tracker.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads/updates the authenticated user's profile. The userId is the trusted subject the
 * gateway extracted from the JWT and forwarded as X-User-Id — never taken from the body.
 */
@Service
public class ProfileService {

    private final UserAccountRepository users;

    public ProfileService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(String userId) {
        return toResponse(load(userId));
    }

    @Transactional
    public ProfileResponse update(String userId, UpdateProfileRequest req) {
        UserAccount user = load(userId);
        user.setFirstName(trimToNull(req.firstName()));
        user.setLastName(trimToNull(req.lastName()));
        user.setPhone(trimToNull(req.phone()));
        user.setLocation(trimToNull(req.location()));
        user.setHeadline(trimToNull(req.headline()));
        users.save(user);
        return toResponse(user);
    }

    private UserAccount load(String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new InvalidCredentialsException("Invalid user id");
        }
        return users.findById(id)
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));
    }

    private static ProfileResponse toResponse(UserAccount u) {
        return new ProfileResponse(
                u.getId().toString(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.getLocation(),
                u.getHeadline());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
