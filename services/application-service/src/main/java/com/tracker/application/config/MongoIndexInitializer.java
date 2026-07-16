package com.tracker.application.config;

import com.tracker.application.domain.Application;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject.Type;
import org.springframework.stereotype.Component;

/**
 * Creates the exact index set from the spec on startup. auto-index-creation is off so
 * that these definitions — including the two partial unique indexes — are authoritative.
 */
@Component
public class MongoIndexInitializer {

    private final MongoTemplate mongo;

    public MongoIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexOperations ops = mongo.indexOps(Application.class);

        // { applicationId: 1 } UNIQUE
        ops.ensureIndex(new Index().on("applicationId", Direction.ASC).unique());

        // { userId, portalId, externalJobId } UNIQUE  where externalJobId exists && != null.
        // MongoDB forbids $ne in a partialFilterExpression, so express "present and non-null"
        // as $type: "string" — a null value has BSON type null, not string, so it is excluded.
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC)
                .on("portalId", Direction.ASC)
                .on("externalJobId", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(
                        Criteria.where("externalJobId").type(Type.STRING))));

        // { userId, portalId, jobUrl } UNIQUE  where externalJobId == null
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC)
                .on("portalId", Direction.ASC)
                .on("jobUrl", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(
                        Criteria.where("externalJobId").is(null))));

        // board / sidebar supporting indexes
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC).on("archived", Direction.ASC).on("bucket", Direction.ASC));
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC).on("archived", Direction.ASC).on("dateApplied", Direction.DESC));
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC).on("archived", Direction.ASC).on("jobTitle", Direction.ASC));

        // archival-worker supporting index
        ops.ensureIndex(new Index()
                .on("userId", Direction.ASC).on("bucket", Direction.ASC).on("completedAt", Direction.ASC));
    }
}
