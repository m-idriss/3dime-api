package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dime.api.feature.notion.NotionClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

/**
 * Background Notion sync service for quota data.
 */
@Slf4j
@ApplicationScoped
public class NotionQuotaService {

    @Inject
    @RestClient
    NotionClient notionClient;

    @ConfigProperty(name = "notion.token")
    String token;

    @ConfigProperty(name = "notion.quota.database-id")
    Optional<String> quotaDbId;

    @ConfigProperty(name = "notion.version")
    String version;

    @Inject
    ObjectMapper objectMapper;

    private boolean isEnabled() {
        return quotaDbId.isPresent() && !quotaDbId.get().trim().isEmpty();
    }

    private String getPageId(String userId) {
        if (!isEnabled())
            return null;

        try {
            ObjectNode query = objectMapper.createObjectNode();
            ObjectNode filter = query.putObject("filter");
            filter.put("property", "User ID");
            filter.putObject("title").put("equals", userId);

            JsonNode response = notionClient.queryDatabase(bearerToken(token), version, quotaDbId.get(), query);

            if (response.has("results") && response.get("results").isArray() && response.get("results").size() > 0) {
                return response.get("results").get(0).get("id").asText();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get Notion page ID for user {} (non-blocking)", userId, e);
            return null;
        }
    }

    public void syncToNotion(String userId, long quotaUsed, PlanType plan, Instant periodStart) {
        if (!isEnabled())
            return;

        try {
            String pageId = getPageId(userId);
            ObjectNode properties = objectMapper.createObjectNode();

            addTitleProperty(properties, "User ID", userId);
            addNumberProperty(properties, "Usage Count", quotaUsed);
            addDateProperty(properties, "Last Reset", periodStart.toString());
            addSelectProperty(properties, "Plan", plan.name());

            String authToken = bearerToken(token);

            if (pageId != null) {
                ObjectNode updatePayload = objectMapper.createObjectNode();
                updatePayload.set("properties", properties);
                notionClient.updatePage(authToken, version, pageId, updatePayload);
                log.info("Synced quota to Notion (updated) for user {}", userId);
            } else {
                ObjectNode createPayload = objectMapper.createObjectNode();
                createPayload.putObject("parent").put("database_id", quotaDbId.get());
                createPayload.set("properties", properties);
                notionClient.createPage(authToken, version, createPayload);
                log.info("Synced quota to Notion (created) for user {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync to Notion for user {} (non-blocking)", userId, e);
        }
    }

    private void addTitleProperty(ObjectNode properties, String name, String content) {
        ObjectNode titleWrapper = properties.putObject(name);
        ArrayNode titleArray = titleWrapper.putArray("title");
        titleArray.addObject().putObject("text").put("content", content);
    }

    private void addNumberProperty(ObjectNode properties, String name, Number value) {
        properties.putObject(name).put("number", value.doubleValue());
    }

    private void addDateProperty(ObjectNode properties, String name, String date) {
        properties.putObject(name).putObject("date").put("start", date);
    }

    private void addSelectProperty(ObjectNode properties, String name, String option) {
        properties.putObject(name).putObject("select").put("name", option);
    }

    private String bearerToken(String raw) {
        if (raw == null)
            return null;
        return raw.startsWith("Bearer ") ? raw : "Bearer " + raw;
    }

    public record QuotaData(long usageCount, Instant lastReset, PlanType plan) {
    }
}
