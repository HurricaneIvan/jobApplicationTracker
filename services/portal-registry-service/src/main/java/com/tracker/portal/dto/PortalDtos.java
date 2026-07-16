package com.tracker.portal.dto;

import com.tracker.portal.domain.Portal;
import jakarta.validation.constraints.NotBlank;

/**
 * Request/response DTOs, kept strictly separate from the {@link Portal} entity.
 */
public final class PortalDtos {

    private PortalDtos() { }

    /** POST /portals body. Server normalizes the domain and generates portalId. */
    public record CreatePortalRequest(
            @NotBlank String domain,
            @NotBlank String displayName
    ) { }

    /** Outgoing portal representation. */
    public record PortalResponse(
            String portalId,
            String domain,
            String displayName
    ) {
        public static PortalResponse from(Portal p) {
            return new PortalResponse(p.getPortalId(), p.getDomain(), p.getDisplayName());
        }
    }
}
