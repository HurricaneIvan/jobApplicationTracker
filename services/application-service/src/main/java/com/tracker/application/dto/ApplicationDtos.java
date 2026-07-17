package com.tracker.application.dto;

import com.tracker.application.domain.Application;
import com.tracker.application.domain.Bucket;
import com.tracker.application.domain.Stage;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * Request/response DTOs, kept strictly separate from the {@link Application} entity.
 * userId is NEVER accepted from the client — it is taken from the JWT.
 */
public final class ApplicationDtos {

    private ApplicationDtos() { }

    /** POST /applications body. Server generates applicationId; bucket defaults to applied. */
    public record CreateApplicationRequest(
            String externalJobId,                 // nullable
            @NotBlank String portalId,
            @NotBlank String portalName,
            @NotBlank String jobUrl,
            @NotBlank String jobTitle,
            @NotBlank String company,
            Instant dateApplied,                  // nullable -> defaults to now
            String notes,                         // nullable
            String descriptionSnapshot            // nullable
    ) { }

    /** PATCH /applications/{id}/status */
    public record UpdateStatusRequest(@NotBlank String bucket) {
        public Bucket bucketEnum() { return Bucket.fromWire(bucket); }
    }

    /** PATCH /applications/{id}/notes */
    public record UpdateNotesRequest(String notes) { }

    /** PATCH /applications/{id}/stage — sub-stage within In Progress. Blank/null/"none" clears it. */
    public record UpdateStageRequest(String stage) {
        public Stage stageEnum() { return Stage.fromWire(stage); }
    }

    /** PATCH /applications/{id} — general edit. All fields optional (null = leave unchanged). */
    public record UpdateApplicationRequest(
            String jobTitle,
            String company,
            String jobUrl,
            String externalJobId,
            String notes,
            String companyDescription,
            String descriptionSnapshot
    ) { }

    /** Outgoing tile representation. */
    public record ApplicationResponse(
            String applicationId,
            String externalJobId,
            String portalId,
            String portalName,
            String jobUrl,
            String jobTitle,
            String company,
            String bucket,
            String stage,
            String applicationCopyS3Key,
            boolean hasDocument,
            String notes,
            String companyDescription,
            Instant dateApplied,
            Instant completedAt,
            boolean archived,
            String descriptionSnapshot
    ) {
        public static ApplicationResponse from(Application a) {
            return new ApplicationResponse(
                    a.getApplicationId(),
                    a.getExternalJobId(),
                    a.getPortalId(),
                    a.getPortalName(),
                    a.getJobUrl(),
                    a.getJobTitle(),
                    a.getCompany(),
                    a.getBucket().wire(),
                    a.getStage() != null ? a.getStage().wire() : null,
                    a.getApplicationCopyS3Key(),
                    a.getApplicationCopyS3Key() != null,
                    a.getNotes(),
                    a.getCompanyDescription(),
                    a.getDateApplied(),
                    a.getCompletedAt(),
                    a.isArchived(),
                    a.getDescriptionSnapshot()
            );
        }
    }

    /** GET /applications — board grouped by bucket. */
    public record BoardResponse(
            List<ApplicationResponse> applied,
            List<ApplicationResponse> inProgress,
            List<ApplicationResponse> complete,
            List<ApplicationResponse> archived
    ) { }

    /** Presigned S3 responses. */
    public record PresignedPutResponse(String uploadUrl, String s3Key, long expiresInSeconds) { }
    public record PresignedGetResponse(String downloadUrl, String s3Key, long expiresInSeconds) { }
}
