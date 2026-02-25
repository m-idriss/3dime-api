package com.dime.api.feature.shared;

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

    void onStart(@Observes StartupEvent event) {
        log.info("Starting async cache warmup...");
        CompletableFuture.runAsync(this::warmCaches);
    }

    private void warmCaches() {
        try {
            gitHubService.getUserInfo();
            log.info("GitHub user cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to warm GitHub user cache: {}", e.getMessage());
        }
        try {
            gitHubService.getSocialAccounts();
            log.info("GitHub social cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to warm GitHub social cache: {}", e.getMessage());
        }
        try {
            notionService.getCmsContent();
            log.info("Notion CMS cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to warm Notion CMS cache: {}", e.getMessage());
        }
        log.info("Cache warmup completed");
    }
}
