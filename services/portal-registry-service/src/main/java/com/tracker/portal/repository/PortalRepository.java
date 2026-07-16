package com.tracker.portal.repository;

import com.tracker.portal.domain.Portal;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PortalRepository extends MongoRepository<Portal, String> {

    Optional<Portal> findByDomain(String domain);

    Optional<Portal> findByPortalId(String portalId);

    List<Portal> findAll(Sort sort);
}
