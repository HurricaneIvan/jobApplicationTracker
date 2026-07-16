package com.tracker.portal.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A known job portal. Persisted in the `portals` collection.
 *
 *  - portalId    : internal server-generated UUID; the stable identifier used by clients.
 *  - domain      : normalized host (no scheme, no leading www., no trailing slash, lowercase).
 *                  Unique across the registry — it is the natural dedup key.
 *  - displayName : human-friendly label shown in the UI (e.g. "LinkedIn").
 */
@Document(collection = "portals")
public class Portal {

    @Id
    private String id;                 // Mongo _id (ObjectId hex)

    @Field("portalId")
    private String portalId;           // internal UUID (unique)

    @Field("domain")
    private String domain;             // normalized, unique

    private String displayName;

    public Portal() { }

    public Portal(String portalId, String domain, String displayName) {
        this.portalId = portalId;
        this.domain = domain;
        this.displayName = displayName;
    }

    // --- getters / setters ---------------------------------------------------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPortalId() { return portalId; }
    public void setPortalId(String portalId) { this.portalId = portalId; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
