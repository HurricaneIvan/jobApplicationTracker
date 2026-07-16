package com.tracker.archival.job;

import com.mongodb.client.result.UpdateResult;
import com.tracker.archival.config.ArchivalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Hourly sweep that archives long-completed application tiles.
 *
 * <p>Cadence: runs at the top of every hour by default (see {@code archival.cron}). It operates
 * on the {@code applications} collection in the shared `tracker` database — that collection is
 * OWNED by application-service (CONTRACT.md); this worker only flips {@code archived}/{@code bucket}.
 *
 * <p>Bucket wire values are the lowercase strings applied|in_progress|complete|archived (see
 * application-service {@code Bucket.java}).
 */
@Component
public class ArchivalJob {

    private static final Logger log = LoggerFactory.getLogger(ArchivalJob.class);

    private static final String COLLECTION = "applications";
    private static final String BUCKET_COMPLETE = "complete";
    private static final String BUCKET_ARCHIVED = "archived";

    private final MongoTemplate mongoTemplate;
    private final ArchivalProperties props;

    public ArchivalJob(MongoTemplate mongoTemplate, ArchivalProperties props) {
        this.mongoTemplate = mongoTemplate;
        this.props = props;
    }

    /**
     * Find tiles where bucket == "complete" AND archived == false AND completedAt is older than
     * the retention window, then bulk-set archived = true and bucket = "archived".
     */
    @Scheduled(cron = "${archival.cron:0 0 * * * *}")
    public void archiveCompletedTiles() {
        Instant cutoff = Instant.now().minus(props.archiveAfterDays(), ChronoUnit.DAYS);

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("bucket").is(BUCKET_COMPLETE),
                Criteria.where("archived").is(false),
                Criteria.where("completedAt").lt(cutoff)
        ));

        Update update = new Update()
                .set("archived", true)
                .set("bucket", BUCKET_ARCHIVED);

        UpdateResult result = mongoTemplate.updateMulti(query, update, COLLECTION);
        log.info("Archival sweep complete: archived {} tile(s) completed before {} (retention {} days)",
                result.getModifiedCount(), cutoff, props.archiveAfterDays());
    }
}
