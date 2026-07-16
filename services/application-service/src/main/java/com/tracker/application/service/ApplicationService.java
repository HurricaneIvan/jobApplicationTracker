package com.tracker.application.service;

import com.tracker.application.document.DocumentService;
import com.tracker.application.domain.Application;
import com.tracker.application.domain.Bucket;
import com.tracker.application.dto.ApplicationDtos.ApplicationResponse;
import com.tracker.application.dto.ApplicationDtos.BoardResponse;
import com.tracker.application.dto.ApplicationDtos.CreateApplicationRequest;
import com.tracker.application.dto.ApplicationDtos.PresignedGetResponse;
import com.tracker.application.dto.ApplicationDtos.PresignedPutResponse;
import com.tracker.application.dto.ApplicationDtos.UpdateApplicationRequest;
import com.tracker.application.exception.DuplicateApplicationException;
import com.tracker.application.exception.NotFoundException;
import com.tracker.application.repository.ApplicationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationService {

    private final ApplicationRepository repo;
    private final SidebarCacheService sidebarCache;
    private final DocumentService documentService;

    public ApplicationService(ApplicationRepository repo, SidebarCacheService sidebarCache,
                              DocumentService documentService) {
        this.repo = repo;
        this.sidebarCache = sidebarCache;
        this.documentService = documentService;
    }

    // ------------------------------------------------------------------ create
    public ApplicationResponse create(String userId, CreateApplicationRequest req) {
        Optional<Application> existing = findDuplicate(userId, req.portalId(), req.externalJobId(), req.jobUrl());
        if (existing.isPresent()) {
            throw new DuplicateApplicationException(ApplicationResponse.from(existing.get()));
        }

        Application a = new Application();
        a.setApplicationId(UUID.randomUUID().toString());   // server-side UUID — the routing key
        a.setExternalJobId(blankToNull(req.externalJobId()));
        a.setUserId(userId);
        a.setPortalId(req.portalId());
        a.setPortalName(req.portalName());
        a.setJobUrl(req.jobUrl());
        a.setJobTitle(req.jobTitle());
        a.setCompany(req.company());
        a.setBucket(Bucket.APPLIED);
        a.setNotes(req.notes());
        a.setDateApplied(req.dateApplied() != null ? req.dateApplied() : Instant.now());
        a.setArchived(false);
        a.setDescriptionSnapshot(req.descriptionSnapshot());

        try {
            repo.save(a);
        } catch (DuplicateKeyException e) {
            // Lost a race against a concurrent create — return the winner as a 409.
            Application winner = findDuplicate(userId, req.portalId(), req.externalJobId(), req.jobUrl())
                    .orElseThrow(() -> e);
            throw new DuplicateApplicationException(ApplicationResponse.from(winner));
        }
        sidebarCache.invalidate(userId);
        return ApplicationResponse.from(a);
    }

    private Optional<Application> findDuplicate(String userId, String portalId, String externalJobId, String jobUrl) {
        String ext = blankToNull(externalJobId);
        if (ext != null) {
            return repo.findByUserIdAndPortalIdAndExternalJobId(userId, portalId, ext);
        }
        return repo.findByUserIdAndPortalIdAndJobUrl(userId, portalId, jobUrl);
    }

    // ------------------------------------------------------------------- board
    public BoardResponse getBoard(String userId, boolean includeArchived) {
        List<Application> docs = includeArchived
                ? repo.findByUserId(userId)
                : repo.findByUserIdAndArchivedFalse(userId);
        return new BoardResponse(
                mapBucket(docs, Bucket.APPLIED),
                mapBucket(docs, Bucket.IN_PROGRESS),
                mapBucket(docs, Bucket.COMPLETE),
                mapBucket(docs, Bucket.ARCHIVED));
    }

    private List<ApplicationResponse> mapBucket(List<Application> docs, Bucket bucket) {
        return docs.stream()
                .filter(a -> a.getBucket() == bucket)
                .map(ApplicationResponse::from)
                .toList();
    }

    // ----------------------------------------------------------------- sidebar
    public List<ApplicationResponse> getSidebar(String userId, String sort, String order) {
        String s = normalizeSort(sort);
        String o = normalizeOrder(order);

        Optional<List<ApplicationResponse>> cached = sidebarCache.get(userId, s, o);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Non-archived only — archived tiles never appear in the sidebar.
        List<Application> docs = repo.findByUserIdAndArchivedFalse(userId);
        Comparator<Application> comparator = switch (s) {
            case "title" -> Comparator.comparing(a -> safe(a.getJobTitle()).toLowerCase());
            case "status" -> Comparator.comparingInt(a -> a.getBucket().ordinal());  // applied<in_progress<complete
            default -> Comparator.comparing(Application::getDateApplied,
                    Comparator.nullsLast(Comparator.naturalOrder()));                 // dateApplied
        };
        if ("desc".equals(o)) {
            comparator = comparator.reversed();
        }
        List<ApplicationResponse> result = docs.stream()
                .sorted(comparator)
                .map(ApplicationResponse::from)
                .toList();

        sidebarCache.put(userId, s, o, result);
        return result;
    }

    // -------------------------------------------------------------------- read
    public ApplicationResponse getOne(String userId, String applicationId) {
        return ApplicationResponse.from(load(userId, applicationId));
    }

    // ------------------------------------------------------- status transition
    public ApplicationResponse updateStatus(String userId, String applicationId, Bucket newBucket) {
        Application a = load(userId, applicationId);
        Bucket old = a.getBucket();

        // Archival-clock rule:
        if (newBucket == Bucket.COMPLETE && old != Bucket.COMPLETE) {
            a.setCompletedAt(Instant.now());          // first time (re)marked Complete -> start clock
        } else if (old == Bucket.COMPLETE && newBucket != Bucket.COMPLETE) {
            a.setCompletedAt(null);                    // moved out before archival -> reset clock
        }

        a.setBucket(newBucket);
        a.setArchived(newBucket == Bucket.ARCHIVED);   // keep archived flag consistent with the column

        repo.save(a);
        sidebarCache.invalidate(userId);
        return ApplicationResponse.from(a);
    }

    // -------------------------------------------------------------------- notes
    public ApplicationResponse updateNotes(String userId, String applicationId, String notes) {
        Application a = load(userId, applicationId);
        a.setNotes(notes);
        repo.save(a);
        sidebarCache.invalidate(userId);
        return ApplicationResponse.from(a);
    }

    // ------------------------------------------------------------ general edit
    public ApplicationResponse update(String userId, String applicationId, UpdateApplicationRequest req) {
        Application a = load(userId, applicationId);
        if (req.jobTitle() != null) a.setJobTitle(req.jobTitle());
        if (req.company() != null) a.setCompany(req.company());
        if (req.jobUrl() != null) a.setJobUrl(req.jobUrl());
        if (req.externalJobId() != null) a.setExternalJobId(blankToNull(req.externalJobId()));
        if (req.notes() != null) a.setNotes(req.notes());
        if (req.descriptionSnapshot() != null) a.setDescriptionSnapshot(req.descriptionSnapshot());
        repo.save(a);
        sidebarCache.invalidate(userId);
        return ApplicationResponse.from(a);
    }

    // ------------------------------------------------------------------ delete
    public void delete(String userId, String applicationId) {
        Application a = load(userId, applicationId);
        repo.delete(a);
        sidebarCache.invalidate(userId);
    }

    // --------------------------------------------------------------- documents
    public PresignedPutResponse presignDocumentPut(String userId, String applicationId, String contentType) {
        Application a = load(userId, applicationId);
        PresignedPutResponse resp = documentService.presignPut(userId, applicationId, contentType);
        a.setApplicationCopyS3Key(resp.s3Key());
        repo.save(a);
        sidebarCache.invalidate(userId);
        return resp;
    }

    public PresignedGetResponse presignDocumentGet(String userId, String applicationId) {
        Application a = load(userId, applicationId);
        if (a.getApplicationCopyS3Key() == null) {
            throw new NotFoundException("No document uploaded for this application");
        }
        return documentService.presignGet(a.getApplicationCopyS3Key());
    }

    // ------------------------------------------------------------------ helpers
    private Application load(String userId, String applicationId) {
        return repo.findByApplicationIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));
    }

    private static String normalizeSort(String sort) {
        if (sort == null) return "dateApplied";
        return switch (sort) {
            case "title", "status", "dateApplied" -> sort;
            default -> "dateApplied";
        };
    }

    private static String normalizeOrder(String order) {
        return "asc".equalsIgnoreCase(order) ? "asc" : "desc";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
