package com.tracker.archival;

import com.tracker.archival.config.ArchivalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Archival worker (port 8084).
 *
 * Runs an hourly scheduled sweep that moves long-completed application tiles into the
 * "archived" bucket. It shares the `applications` collection written by application-service
 * (see CONTRACT.md) and only ever flips the archival flags — it does not own that data.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ArchivalProperties.class)
public class ArchivalWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArchivalWorkerApplication.class, args);
    }
}
