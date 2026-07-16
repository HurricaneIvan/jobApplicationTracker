package com.tracker.application.web;

import com.tracker.application.dto.ApplicationDtos.ApplicationResponse;
import com.tracker.application.dto.ApplicationDtos.BoardResponse;
import com.tracker.application.dto.ApplicationDtos.CreateApplicationRequest;
import com.tracker.application.dto.ApplicationDtos.UpdateApplicationRequest;
import com.tracker.application.dto.ApplicationDtos.UpdateNotesRequest;
import com.tracker.application.dto.ApplicationDtos.UpdateStatusRequest;
import com.tracker.application.security.UserContext;
import com.tracker.application.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        ApplicationResponse created = service.create(UserContext.getUserId(), req);
        return ResponseEntity
                .created(URI.create("/api/v1/applications/" + created.applicationId()))
                .body(created);
    }

    /** Board grouped by bucket. ?includeArchived=true loads the Archived column too. */
    @GetMapping
    public BoardResponse board(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.getBoard(UserContext.getUserId(), includeArchived);
    }

    /** Non-archived flat list. ?sort=dateApplied|title|status ?order=asc|desc */
    @GetMapping("/sidebar")
    public List<ApplicationResponse> sidebar(
            @RequestParam(defaultValue = "dateApplied") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        return service.getSidebar(UserContext.getUserId(), sort, order);
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse getOne(@PathVariable String applicationId) {
        return service.getOne(UserContext.getUserId(), applicationId);
    }

    @PatchMapping("/{applicationId}/status")
    public ApplicationResponse updateStatus(@PathVariable String applicationId,
                                            @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(UserContext.getUserId(), applicationId, req.bucketEnum());
    }

    @PatchMapping("/{applicationId}/notes")
    public ApplicationResponse updateNotes(@PathVariable String applicationId,
                                           @RequestBody UpdateNotesRequest req) {
        return service.updateNotes(UserContext.getUserId(), applicationId, req.notes());
    }

    @PatchMapping("/{applicationId}")
    public ApplicationResponse update(@PathVariable String applicationId,
                                      @RequestBody UpdateApplicationRequest req) {
        return service.update(UserContext.getUserId(), applicationId, req);
    }

    @DeleteMapping("/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String applicationId) {
        service.delete(UserContext.getUserId(), applicationId);
    }
}
