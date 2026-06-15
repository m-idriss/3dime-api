package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dime.api.feature.notion.NotionClient;
import com.dime.api.feature.shared.BearerTokenUtil;
import com.dime.api.feature.shared.FirestoreCacheService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class TrackingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final int NOTION_QUERY_PAGE_SIZE = 100;

    @Inject
    @RestClient
    NotionClient notionClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "notion.token")
    Optional<String> notionToken;

    @ConfigProperty(name = "notion.tracking.database-id")
    Optional<String> trackingDbId;

    @ConfigProperty(name = "notion.version")
    String notionVersion;

    @ConfigProperty(name = "notion.user-id")
    Optional<String> assignedUserId;

    @Inject
    FirestoreCacheService firestoreCacheService;

    LoadingCache<String, Statistics> statisticsCache;

    @PostConstruct
    void initCaches() {
        statisticsCache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofMinutes(5))
                .expireAfterWrite(Duration.ofHours(1))
                .build(key -> fetchStatistics());
    }

    private boolean isEnabled() {
        return notionToken.isPresent() && trackingDbId.isPresent();
    }

    public void logConversion(String userId, String email, int fileCount, String domain, int eventCount, long duration) {
        logEvent("conversion", userId, email, "Success", fileCount, eventCount, duration, null, domain);
    }

    public void logConversionError(String userId, String email, int fileCount, String errorMessage, long duration, String domain) {
        logEvent("conversion", userId, email, "Error", fileCount, 0, duration, errorMessage, domain);
    }

    public void logQuotaExceeded(String userId, String email, int usageCount, int limit, String plan, String domain) {
        String errorMessage = String.format("Quota exceeded: %d/%d (plan: %s)", usageCount, limit, plan);
        logEvent("quota_exceeded", userId, email, "Error", usageCount, limit, 0, errorMessage, domain);
    }

    private void logEvent(String action, String userId, String email, String status, int fileCount, int eventCount, long duration,
            String errorMessage, String domain) {
        if (!isEnabled()) {
            log.debug("Tracking disabled: Notion token or DB ID missing");
            return;
        }

        try {
            ObjectNode parent = objectMapper.createObjectNode();
            parent.put("type", "database_id");
            parent.put("database_id", trackingDbId.get());

            ObjectNode properties = objectMapper.createObjectNode();
            addTitleProperty(properties, "Action", action);
            addRichTextProperty(properties, "User ID", userId);
            if (email != null && !email.isBlank()) {
                addRichTextProperty(properties, "Email", email);
            }
            addDateProperty(properties, "Timestamp", Instant.now().toString());
            addSelectProperty(properties, "Status", status);

            if (domain != null)
                addRichTextProperty(properties, "Domain", domain);
            addNumberProperty(properties, "File Count", fileCount);
            addNumberProperty(properties, "Event Count", eventCount);
            addNumberProperty(properties, "Duration (ms)", duration);

            if (errorMessage != null) {
                String truncatedError = errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH
                        ? errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                        : errorMessage;
                addRichTextProperty(properties, "Error Message", truncatedError);
            }

            // Add Assigned property with mention if userId is configured
            if (assignedUserId.isPresent() && !assignedUserId.get().trim().isEmpty()) {
                addMentionProperty(properties, "Assigned", assignedUserId.get());
            }

            ObjectNode page = objectMapper.createObjectNode();
            page.set("parent", parent);
            page.set("properties", properties);

            notionClient.createPage(BearerTokenUtil.ensureBearer(notionToken.get()), notionVersion, page);
            log.info("Logged usage event: {} for user {}", action, userId);

        } catch (Exception e) {
            log.error("Failed to log usage event to Notion: {}", e.getMessage(), e);
        }
    }

    public Statistics getStatistics() {
        return statisticsCache.get("default");
    }

    public void warmFromFirestore() {
        if (firestoreCacheService == null) return;
        firestoreCacheService.read("statistics", Statistics.class)
                .ifPresent(stats -> statisticsCache.put("default", stats));
    }

    private Statistics fetchStatistics() {
        if (!isEnabled()) {
            return new Statistics(0, 0);
        }

        try {
            ObjectNode baseQuery = objectMapper.createObjectNode();
            ArrayNode and = baseQuery.putObject("filter").putArray("and");

            ObjectNode actionFilter = and.addObject();
            actionFilter.put("property", "Action");
            actionFilter.putObject("title").put("equals", "conversion");

            ObjectNode statusFilter = and.addObject();
            statusFilter.put("property", "Status");
            statusFilter.putObject("select").put("equals", "Success");

            int totalFileCount = 0;
            int totalEventCount = 0;
            boolean hasMore;
            String nextCursor = null;
            int pageCount = 0;
            final int MAX_PAGES = 1000;

            do {
                ObjectNode query = baseQuery.deepCopy();
                query.put("page_size", NOTION_QUERY_PAGE_SIZE);
                if (nextCursor != null && !nextCursor.isBlank()) {
                    query.put("start_cursor", nextCursor);
                }

                JsonNode response = notionClient.queryDatabase(BearerTokenUtil.ensureBearer(notionToken.get()), notionVersion,
                        trackingDbId.get(), query);

                if (response.has("results") && response.get("results").isArray()) {
                    for (JsonNode page : response.get("results")) {
                        JsonNode props = page.get("properties");
                        if (props != null) {
                            totalFileCount += extractNumberProperty(props, "File Count");
                            totalEventCount += extractNumberProperty(props, "Event Count");
                        }
                    }
                }

                hasMore = response.path("has_more").asBoolean(false);
                JsonNode nextCursorNode = response.get("next_cursor");
                nextCursor = nextCursorNode != null && !nextCursorNode.isNull()
                        ? nextCursorNode.asText()
                        : null;
                pageCount++;
            } while (hasMore && nextCursor != null && !nextCursor.isBlank() && pageCount < MAX_PAGES);

            log.info("Fetched statistics from Notion: fileCount={}, eventCount={}", totalFileCount, totalEventCount);
            Statistics stats = new Statistics(totalFileCount, totalEventCount);
            if (firestoreCacheService != null) firestoreCacheService.write("statistics", stats);
            return stats;

        } catch (Exception e) {
            log.error("Failed to fetch statistics from Notion", e);
            Statistics fallback = statisticsCache != null ? statisticsCache.getIfPresent("default") : null;
            if (fallback != null) {
                log.warn("Using cached statistics fallback after Notion fetch failure: fileCount={}, eventCount={}",
                        fallback.fileCount(), fallback.eventCount());
                return fallback;
            }
            if (firestoreCacheService != null) {
                Optional<Statistics> firestoreFallback = firestoreCacheService.read("statistics", Statistics.class);
                if (firestoreFallback.isPresent()) {
                    Statistics stats = firestoreFallback.get();
                    log.warn("Using Firestore statistics fallback after Notion fetch failure: fileCount={}, eventCount={}",
                            stats.fileCount(), stats.eventCount());
                    return stats;
                }
            }
            return new Statistics(0, 0);
        }
    }

    private int extractNumberProperty(JsonNode properties, String propertyName) {
        if (properties == null || !properties.has(propertyName)) {
            return 0;
        }
        JsonNode numberNode = properties.get(propertyName).get("number");
        return numberNode != null && !numberNode.isNull() ? numberNode.asInt(0) : 0;
    }

    @Schema(description = "Global conversion statistics aggregated from Notion tracking database")
    public record Statistics(
            @Schema(description = "Total number of image files processed in successful conversions") int fileCount,
            @Schema(description = "Total number of calendar events generated in successful conversions") int eventCount) {
    }

    void addTitleProperty(ObjectNode properties, String name, String content) {
        ObjectNode title = properties.putObject(name).putArray("title").addObject();
        title.putObject("text").put("content", content);
    }

    void addRichTextProperty(ObjectNode properties, String name, String content) {
        ObjectNode richText = properties.putObject(name).putArray("rich_text").addObject();
        richText.putObject("text").put("content", content);
    }

    void addDateProperty(ObjectNode properties, String name, String date) {
        properties.putObject(name).putObject("date").put("start", date);
    }

    void addSelectProperty(ObjectNode properties, String name, String option) {
        properties.putObject(name).putObject("select").put("name", option);
    }

    void addNumberProperty(ObjectNode properties, String name, Number value) {
        properties.putObject(name).put("number", value.doubleValue());
    }

    private void addMentionProperty(ObjectNode properties, String name, String userId) {
        ObjectNode mentionWrapper = properties.putObject(name);
        ArrayNode richTextArray = mentionWrapper.putArray("rich_text");
        ObjectNode mentionPart = richTextArray.addObject();
        mentionPart.put("type", "mention");
        ObjectNode mention = mentionPart.putObject("mention");
        mention.put("type", "user");
        mention.putObject("user").put("id", userId);
    }

}
