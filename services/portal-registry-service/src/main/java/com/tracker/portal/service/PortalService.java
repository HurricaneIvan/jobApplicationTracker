package com.tracker.portal.service;

import com.tracker.portal.domain.Portal;
import com.tracker.portal.dto.PortalDtos.CreatePortalRequest;
import com.tracker.portal.dto.PortalDtos.PortalResponse;
import com.tracker.portal.exception.DuplicatePortalException;
import com.tracker.portal.repository.PortalRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PortalService {

    private final PortalRepository repo;

    public PortalService(PortalRepository repo) {
        this.repo = repo;
    }

    // -------------------------------------------------------------------- list
    /** All known portals, sorted by domain — used by the extension and frontend to sync. */
    public List<PortalResponse> list() {
        return repo.findAll(Sort.by(Sort.Direction.ASC, "domain")).stream()
                .map(PortalResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ create
    public PortalResponse create(CreatePortalRequest req) {
        String domain = normalizeDomain(req.domain());
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain must resolve to a non-empty host");
        }

        repo.findByDomain(domain).ifPresent(existing -> {
            throw new DuplicatePortalException(PortalResponse.from(existing));
        });

        Portal p = new Portal(UUID.randomUUID().toString(), domain, req.displayName());
        try {
            repo.save(p);
        } catch (DuplicateKeyException e) {
            // Lost a race against a concurrent create — return the winner as a 409.
            Portal winner = repo.findByDomain(domain).orElseThrow(() -> e);
            throw new DuplicatePortalException(PortalResponse.from(winner));
        }
        return PortalResponse.from(p);
    }

    /**
     * Normalize a domain to its canonical form: strip scheme, any path/query, a leading
     * "www.", and a trailing slash, then lowercase.
     * e.g. "https://www.LinkedIn.com/jobs/" -> "linkedin.com"
     */
    public static String normalizeDomain(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.trim().toLowerCase();
        // strip scheme
        int scheme = d.indexOf("://");
        if (scheme >= 0) {
            d = d.substring(scheme + 3);
        }
        // strip any path / query / fragment / port
        int slash = d.indexOf('/');
        if (slash >= 0) {
            d = d.substring(0, slash);
        }
        int at = d.indexOf('@');   // userinfo, just in case
        if (at >= 0) {
            d = d.substring(at + 1);
        }
        int colon = d.indexOf(':'); // port
        if (colon >= 0) {
            d = d.substring(0, colon);
        }
        // strip leading www.
        if (d.startsWith("www.")) {
            d = d.substring(4);
        }
        // strip any trailing dots
        while (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }
}
