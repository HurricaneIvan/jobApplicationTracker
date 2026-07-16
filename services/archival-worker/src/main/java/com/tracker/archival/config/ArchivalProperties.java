package com.tracker.archival.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the archival sweep, bound from the {@code archival.*} keys in application.yml.
 *
 * @param archiveAfterDays retention window; a "complete" tile is archived once its
 *                         {@code completedAt} is older than this many days (env ARCHIVE_AFTER_DAYS).
 * @param cron             6-field Spring cron for the sweep (env ARCHIVAL_CRON; default hourly).
 */
@ConfigurationProperties(prefix = "archival")
public record ArchivalProperties(int archiveAfterDays, String cron) {

    public ArchivalProperties {
        if (archiveAfterDays < 0) {
            throw new IllegalArgumentException("archival.archive-after-days must be >= 0");
        }
    }
}
