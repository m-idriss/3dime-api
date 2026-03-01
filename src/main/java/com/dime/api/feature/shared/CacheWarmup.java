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
        warmFromFirestore();
        CompletableFuture.runAsync(this::warmFromApis);
    }

    private void warmFromFirestore() {
        try {
            gitHubService.warmFromFirestore();
            notionService.warmFromFirestore();
            trackingService.warmFromFirestore();
            log.info("Phase 1: Firestore cache warmup completed (instant data available)");
        } catch (Exception e) {
            log.warn("Firestore cache warmup failed: {}", e.getMessage());
        }
    }

    private void warmFromApis() {
        try {
            gitHubService.getUserInfo();
            log.info("GitHub user cache refreshed from API");
        } catch (Exception e) {
            log.warn("Failed to warm GitHub user cache: {}", e.getMessage());
        }
        try {
            gitHubService.getSocialAccounts();
            log.info("GitHub social cache refreshed from API");
        } catch (Exception e) {
            log.warn("Failed to warm GitHub social cache: {}", e.getMessage());
        }
        try {
            gitHubService.getCommits(12);
            log.info("GitHub commits cache refreshed from API");
        } catch (Exception e) {
            log.warn("Failed to warm GitHub commits cache: {}", e.getMessage());
        }
        try {
            notionService.getCmsContent();
            log.info("Notion CMS cache refreshed from API");
        } catch (Exception e) {
            log.warn("Failed to warm Notion CMS cache: {}", e.getMessage());
        }
        try {
            trackingService.getStatistics();
            log.info("Statistics cache refreshed from API");
        } catch (Exception e) {
            log.warn("Failed to warm statistics cache: {}", e.getMessage());
        }
        log.info("Phase 2: API cache warmup completed");
    }
}
