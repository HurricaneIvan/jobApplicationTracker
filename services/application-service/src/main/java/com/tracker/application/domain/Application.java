package com.tracker.application.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * A single application tile. Persisted in the `applications` collection.
 *
 * NOTE on the two id fields — they are NOT interchangeable:
 *  - applicationId : internal server-generated UUID; the stable primary key used
 *                    in every API route. Never changes.
 *  - externalJobId : the portal's own posting id; nullable; display/dedup only.
 */
@Document(collection = "applications")
public class Application {

    @Id
    private String id;                 // Mongo _id (ObjectId hex)

    @Field("applicationId")
    private String applicationId;      // internal UUID (unique) — the routing key

    @Field("externalJobId")
    private String externalJobId;      // portal posting id — nullable

    private String userId;             // owner (from JWT, never from client)
    private String portalId;
    private String portalName;
    private String jobUrl;
    private String jobTitle;
    private String company;

    private Bucket bucket = Bucket.APPLIED;

    private String applicationCopyS3Key;   // nullable until a document is uploaded
    private String notes;
    private Instant dateApplied;
    private Instant completedAt;           // nullable; drives the 7-day archival clock
    private boolean archived = false;
    private String descriptionSnapshot;    // nullable

    public Application() { }

    // --- getters / setters ---------------------------------------------------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getExternalJobId() { return externalJobId; }
    public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPortalId() { return portalId; }
    public void setPortalId(String portalId) { this.portalId = portalId; }

    public String getPortalName() { return portalName; }
    public void setPortalName(String portalName) { this.portalName = portalName; }

    public String getJobUrl() { return jobUrl; }
    public void setJobUrl(String jobUrl) { this.jobUrl = jobUrl; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public Bucket getBucket() { return bucket; }
    public void setBucket(Bucket bucket) { this.bucket = bucket; }

    public String getApplicationCopyS3Key() { return applicationCopyS3Key; }
    public void setApplicationCopyS3Key(String applicationCopyS3Key) { this.applicationCopyS3Key = applicationCopyS3Key; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getDateApplied() { return dateApplied; }
    public void setDateApplied(Instant dateApplied) { this.dateApplied = dateApplied; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public void setDescriptionSnapshot(String descriptionSnapshot) { this.descriptionSnapshot = descriptionSnapshot; }
}
