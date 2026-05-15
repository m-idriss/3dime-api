package com.dime.api.feature.shared;

import com.dime.api.feature.converter.TrackingService;
import com.dime.api.feature.github.GitHubService;
import com.dime.api.feature.notion.NotionService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
@ApplicationScoped
public class CacheWarmup {

    @Inject
    GitHubService gitHubService;

    @Inject
    NotionService notionService;

    @Inject
    TrackingService trackingService;

    void onStart(@Observes StartupEvent event) {
        log.info("Starting cache warmup...");
        CompletableFuture.runAsync(this::runWarmupPhases);
    }

    void runWarmupPhases() {
        warmFromFirestore();
        warmFromApis();
    }

    void warmFromFirestore() {
        runWarmupStep("Firestore cache warmup", () -> {
            gitHubService.warmFromFirestore();
            notionService.warmFromFirestore();
            trackingService.warmFromFirestore();
            log.info("Phase 1: Firestore cache warmup completed (instant data available)");
        });
    }

    void warmFromApis() {
        runWarmupStep("GitHub user cache refresh", () -> {
            gitHubService.getUserInfo();
            log.info("GitHub user cache refreshed from API");
        });
        runWarmupStep("GitHub social cache refresh", () -> {
            gitHubService.getSocialAccounts();
            log.info("GitHub social cache refreshed from API");
        });
        runWarmupStep("GitHub commits cache refresh", () -> {
            gitHubService.getCommits(12);
            log.info("GitHub commits cache refreshed from API");
        });
        runWarmupStep("GitHub release cache refresh", () -> {
            gitHubService.getLatestRelease();
            log.info("GitHub release cache refreshed from API");
        });
        runWarmupStep("Notion CMS cache refresh", () -> {
            notionService.refreshCmsContent();
            log.info("Notion CMS cache refreshed from API");
        });
        runWarmupStep("Statistics cache refresh", () -> {
            trackingService.getStatistics();
            log.info("Statistics cache refreshed from API");
        });
        log.info("Phase 2: API cache warmup completed");
    }

    private void runWarmupStep(String stepName, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.warn("{} failed during startup warmup: {}", stepName, t.getMessage(), t);
        }
    }
}
