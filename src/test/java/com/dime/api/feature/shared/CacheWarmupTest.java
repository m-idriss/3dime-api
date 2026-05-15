package com.dime.api.feature.shared;

import com.dime.api.feature.converter.TrackingService;
import com.dime.api.feature.github.GitHubService;
import com.dime.api.feature.notion.NotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CacheWarmupTest {

    private CacheWarmup cacheWarmup;
    private GitHubService gitHubService;
    private NotionService notionService;
    private TrackingService trackingService;

    @BeforeEach
    void setup() {
        cacheWarmup = new CacheWarmup();
        gitHubService = mock(GitHubService.class);
        notionService = mock(NotionService.class);
        trackingService = mock(TrackingService.class);

        cacheWarmup.gitHubService = gitHubService;
        cacheWarmup.notionService = notionService;
        cacheWarmup.trackingService = trackingService;
    }

    @Test
    void runWarmupPhases_whenFirestoreWarmupThrowsError_doesNotAbortStartupSequence() {
        doThrow(new NoSuchMethodError("io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry.newClientInterceptor()"))
                .when(gitHubService).warmFromFirestore();

        assertDoesNotThrow(() -> cacheWarmup.runWarmupPhases());

        verify(gitHubService, times(1)).warmFromFirestore();
        verify(gitHubService, times(1)).getUserInfo();
        verify(notionService, times(1)).refreshCmsContent();
        verify(trackingService, times(1)).getStatistics();
    }

    @Test
    void warmFromApis_whenOneWarmupStepThrowsError_continuesRemainingSteps() {
        doThrow(new NoSuchMethodError("boom"))
                .when(gitHubService).getUserInfo();

        assertDoesNotThrow(() -> cacheWarmup.warmFromApis());

        verify(gitHubService, times(1)).getUserInfo();
        verify(gitHubService, times(1)).getSocialAccounts();
        verify(gitHubService, times(1)).getCommits(12);
        verify(gitHubService, times(1)).getLatestRelease();
        verify(notionService, times(1)).refreshCmsContent();
        verify(trackingService, times(1)).getStatistics();
    }
}

