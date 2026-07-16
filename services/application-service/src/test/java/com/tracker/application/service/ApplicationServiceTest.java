package com.tracker.application.service;

import com.tracker.application.document.DocumentService;
import com.tracker.application.domain.Application;
import com.tracker.application.domain.Bucket;
import com.tracker.application.dto.ApplicationDtos.CreateApplicationRequest;
import com.tracker.application.exception.DuplicateApplicationException;
import com.tracker.application.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Focused unit tests for the completedAt archival-clock rule and dedup behavior. */
class ApplicationServiceTest {

    private ApplicationRepository repo;
    private SidebarCacheService cache;
    private ApplicationService service;

    private static final String USER = "user-1";

    @BeforeEach
    void setUp() {
        repo = mock(ApplicationRepository.class);
        cache = mock(SidebarCacheService.class);
        DocumentService documents = mock(DocumentService.class);
        service = new ApplicationService(repo, cache, documents);
        when(repo.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Application tile(String appId, Bucket bucket) {
        Application a = new Application();
        a.setApplicationId(appId);
        a.setUserId(USER);
        a.setBucket(bucket);
        a.setPortalId("p1");
        a.setJobUrl("https://example.com/jobs/1");
        return a;
    }

    @Test
    void marking_complete_sets_completedAt() {
        Application a = tile("app-1", Bucket.IN_PROGRESS);
        when(repo.findByApplicationIdAndUserId("app-1", USER)).thenReturn(Optional.of(a));

        service.updateStatus(USER, "app-1", Bucket.COMPLETE);

        assertThat(a.getCompletedAt()).isNotNull();
        assertThat(a.getBucket()).isEqualTo(Bucket.COMPLETE);
    }

    @Test
    void moving_out_of_complete_resets_completedAt() {
        Application a = tile("app-1", Bucket.COMPLETE);
        a.setCompletedAt(java.time.Instant.now());
        when(repo.findByApplicationIdAndUserId("app-1", USER)).thenReturn(Optional.of(a));

        service.updateStatus(USER, "app-1", Bucket.IN_PROGRESS);

        assertThat(a.getCompletedAt()).isNull();
    }

    @Test
    void dedup_by_externalJobId_returns_existing_via_409() {
        Application existing = tile("app-existing", Bucket.APPLIED);
        existing.setExternalJobId("ext-99");
        when(repo.findByUserIdAndPortalIdAndExternalJobId(USER, "p1", "ext-99"))
                .thenReturn(Optional.of(existing));

        CreateApplicationRequest req = new CreateApplicationRequest(
                "ext-99", "p1", "Portal", "https://example.com/jobs/1",
                "Engineer", "Acme", null, null, null);

        assertThatThrownBy(() -> service.create(USER, req))
                .isInstanceOf(DuplicateApplicationException.class);
    }

    @Test
    void create_generates_server_side_applicationId_and_defaults_to_applied() {
        when(repo.findByUserIdAndPortalIdAndJobUrl(eq(USER), eq("p1"), any()))
                .thenReturn(Optional.empty());

        CreateApplicationRequest req = new CreateApplicationRequest(
                null, "p1", "Portal", "https://example.com/jobs/2",
                "Engineer", "Acme", null, null, null);

        var resp = service.create(USER, req);

        assertThat(resp.applicationId()).isNotBlank();
        assertThat(resp.bucket()).isEqualTo("applied");
        assertThat(resp.externalJobId()).isNull();
    }
}
