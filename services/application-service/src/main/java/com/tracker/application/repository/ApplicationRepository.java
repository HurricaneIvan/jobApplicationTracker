package com.tracker.application.repository;

import com.tracker.application.domain.Application;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends MongoRepository<Application, String> {

    Optional<Application> findByApplicationIdAndUserId(String applicationId, String userId);

    Optional<Application> findByApplicationId(String applicationId);

    /** Board query. */
    List<Application> findByUserId(String userId);

    List<Application> findByUserIdAndArchivedFalse(String userId);

    /** Sidebar query — non-archived only, sorted by the caller-provided Sort. */
    List<Application> findByUserIdAndArchivedFalse(String userId, Sort sort);

    /** Dedup lookups. */
    Optional<Application> findByUserIdAndPortalIdAndExternalJobId(String userId, String portalId, String externalJobId);

    Optional<Application> findByUserIdAndPortalIdAndJobUrl(String userId, String portalId, String jobUrl);
}
