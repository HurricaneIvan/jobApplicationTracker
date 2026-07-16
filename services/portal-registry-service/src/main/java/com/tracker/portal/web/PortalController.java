package com.tracker.portal.web;

import com.tracker.portal.dto.PortalDtos.CreatePortalRequest;
import com.tracker.portal.dto.PortalDtos.PortalResponse;
import com.tracker.portal.service.PortalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portals")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    /** List all known portals — readable with any valid JWT. */
    @GetMapping
    public List<PortalResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<PortalResponse> create(@Valid @RequestBody CreatePortalRequest req) {
        PortalResponse created = service.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/portals/" + created.portalId()))
                .body(created);
    }
}
