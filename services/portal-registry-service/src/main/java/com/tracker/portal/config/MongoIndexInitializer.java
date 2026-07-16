package com.tracker.portal.config;

import com.tracker.portal.domain.Portal;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the exact index set from the spec on startup. auto-index-creation is off so
 * that these definitions are authoritative. Runs before the seeder.
 */
@Component
@Order(0)
public class MongoIndexInitializer {

    private final MongoTemplate mongo;

    public MongoIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexOperations ops = mongo.indexOps(Portal.class);

        // { domain: 1 } UNIQUE — the natural dedup key
        ops.ensureIndex(new Index().on("domain", Direction.ASC).unique());

        // { portalId: 1 } UNIQUE
        ops.ensureIndex(new Index().on("portalId", Direction.ASC).unique());
    }
}
