package com.tracker.auth.web;

import com.tracker.auth.dto.AuthDtos.ProfileResponse;
import com.tracker.auth.dto.AuthDtos.UpdateProfileRequest;
import com.tracker.auth.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user's profile. JWT-protected at the gateway, which validates the token and
 * forwards the trusted X-User-Id header — this service never reads a userId from the body.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    static final String USER_ID_HEADER = "X-User-Id";

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse get(@RequestHeader(USER_ID_HEADER) String userId) {
        return service.get(userId);
    }

    @PutMapping
    public ProfileResponse update(@RequestHeader(USER_ID_HEADER) String userId,
                                  @Valid @RequestBody UpdateProfileRequest req) {
        return service.update(userId, req);
    }
}
