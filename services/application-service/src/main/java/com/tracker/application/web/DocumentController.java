package com.tracker.application.web;

import com.tracker.application.dto.ApplicationDtos.PresignedGetResponse;
import com.tracker.application.dto.ApplicationDtos.PresignedPutResponse;
import com.tracker.application.security.UserContext;
import com.tracker.application.service.ApplicationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Document endpoints for the application-copy file. Delegates to the folded document
 * (S3) package via ApplicationService. The browser uploads/downloads directly to S3
 * using the returned presigned URLs — the file bytes never pass through this service.
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/document")
public class DocumentController {

    private final ApplicationService service;

    public DocumentController(ApplicationService service) {
        this.service = service;
    }

    /** Returns a presigned PUT url + the s3Key that gets stored on the tile. */
    @PostMapping
    public PresignedPutResponse presignPut(@PathVariable String applicationId,
                                           @RequestBody(required = false) Map<String, String> body) {
        String contentType = body == null ? null : body.get("contentType");
        return service.presignDocumentPut(UserContext.getUserId(), applicationId, contentType);
    }

    /** Returns a presigned GET url for the stored application copy. */
    @GetMapping
    public PresignedGetResponse presignGet(@PathVariable String applicationId) {
        return service.presignDocumentGet(UserContext.getUserId(), applicationId);
    }
}
