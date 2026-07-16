package com.tracker.portal.config;

import com.tracker.portal.domain.Portal;
import com.tracker.portal.repository.PortalRepository;
import com.tracker.portal.service.PortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the registry with common job portals when the collection is empty.
 * Runs after {@link MongoIndexInitializer} so the unique indexes already exist.
 */
@Component
@Order(1)
public class PortalSeeder {

    private static final Logger log = LoggerFactory.getLogger(PortalSeeder.class);

    /** displayName -> raw domain (normalized on insert). */
    private static final List<String[]> SEED = List.of(
            new String[]{"LinkedIn", "linkedin.com"},
            new String[]{"Greenhouse", "boards.greenhouse.io"},
            new String[]{"Lever", "jobs.lever.co"},
            new String[]{"Workday", "myworkdayjobs.com"},
            new String[]{"Indeed", "indeed.com"},
            new String[]{"Glassdoor", "glassdoor.com"},
            new String[]{"Wellfound", "wellfound.com"},
            new String[]{"Ashby", "jobs.ashbyhq.com"},
            new String[]{"SmartRecruiters", "smartrecruiters.com"},
            new String[]{"Monster", "monster.com"}
    );

    private final PortalRepository repo;

    public PortalSeeder(PortalRepository repo) {
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (repo.count() > 0) {
            return;   // idempotent — only seed an empty registry
        }
        List<Portal> portals = SEED.stream()
                .map(row -> new Portal(
                        UUID.randomUUID().toString(),
                        PortalService.normalizeDomain(row[1]),
                        row[0]))
                .toList();
        repo.saveAll(portals);
        log.info("Seeded {} portals into an empty registry", portals.size());
    }
}
