package com.threedime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.threedime.api.feature.notion.NotionClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

/**
 * Background Notion sync service for quota data.
 * This service syncs Firestore quota data to Notion for business reporting/CRM.
 * It NEVER blocks API responses and NEVER affects request authorization.
 */
@ApplicationScoped
public class NotionQuotaService {

    private static final Logger LOG = Logger.getLogger(NotionQuotaService.class);

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

    /**
     * Get page ID for a user's quota entry in Notion
     */
    private String getPageId(String userId) {
        if (!isEnabled())
            return null;

        try {
            ObjectNode query = objectMapper.createObjectNode();
            ObjectNode filter = query.putObject("filter");
            filter.put("property", "User ID");
            filter.putObject("title").put("equals", userId);

            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
            JsonNode response = notionClient.queryDatabase(authToken, version, quotaDbId.get(), query);

            if (response.has("results") && response.get("results").isArray() && response.get("results").size() > 0) {
                return response.get("results").get(0).get("id").asText();
            }
            return null;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get Notion page ID for user %s (non-blocking)", userId);
            return null;
        }
    }

    /**
     * Sync user quota to Notion (background, non-blocking).
     * This is called asynchronously after Firestore updates.
     * If it fails, it only logs an error - it NEVER blocks the request.
     */
    public void syncToNotion(String userId, long quotaUsed, PlanType plan, Instant periodStart) {
        if (!isEnabled()) {
            return; // Silently skip if Notion is disabled
        }

        try {
            String pageId = getPageId(userId);
            ObjectNode properties = objectMapper.createObjectNode();

            // User ID (title property)
            ArrayNode titleArray = properties.putArray("User ID").addObject().putArray("title");
            titleArray.addObject().putObject("text").put("content", userId);

            // Usage Count (number property)
            properties.putObject("Usage Count").put("number", quotaUsed);

            // Last Reset (date property)
            properties.putObject("Last Reset").putObject("date").put("start", periodStart.toString());

            // Plan (select property)
            properties.putObject("Plan").putObject("select").put("name", plan.name());

            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;

            if (pageId != null) {
                // Update existing page
                ObjectNode updatePayload = objectMapper.createObjectNode();
                updatePayload.set("properties", properties);

                notionClient.updatePage(authToken, version, pageId, updatePayload);
                LOG.infof("Synced quota to Notion (updated) for user %s", userId);
            } else {
                // Create new page
                ObjectNode createPayload = objectMapper.createObjectNode();
                createPayload.putObject("parent").put("database_id", quotaDbId.get());
                createPayload.set("properties", properties);

                notionClient.createPage(authToken, version, createPayload);
                LOG.infof("Synced quota to Notion (created) for user %s", userId);
            }
        } catch (Exception e) {
            // Only log error - NEVER throw or block
            LOG.warnf(e, "Failed to sync to Notion for user %s (non-blocking)", userId);
        }
    }

    /**
     * Read user data from Notion (for legacy migration or fallback).
     */
    public QuotaData readFromNotion(String userId) {
        if (!isEnabled())
            return null;

        try {
            ObjectNode query = objectMapper.createObjectNode();
            ObjectNode filter = query.putObject("filter");
            filter.put("property", "User ID");
            filter.putObject("title").put("equals", userId);

            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
            JsonNode response = notionClient.queryDatabase(authToken, version, quotaDbId.get(), query);

            if (!response.has("results") || response.get("results").size() == 0) {
                return null;
            }

            JsonNode page = response.get("results").get(0);
            JsonNode props = page.get("properties");

            long usageCount = props.has("Usage Count") && props.get("Usage Count").has("number")
                    ? props.get("Usage Count").get("number").asLong(0)
                    : 0;

            String lastResetStr = props.has("Last Reset") && props.get("Last Reset").has("date")
                    ? props.get("Last Reset").get("date").get("start").asText()
                    : Instant.now().toString();

            String planName = props.has("Plan") && props.get("Plan").has("select")
                    && !props.get("Plan").get("select").isNull()
                            ? props.get("Plan").get("select").get("name").asText("FREE")
                            : "FREE";

            return new QuotaData(
                    usageCount,
                    Instant.parse(lastResetStr),
                    PlanType.valueOf(planName));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to read from Notion for user %s", userId);
            return null;
        }
    }

    public record QuotaData(long usageCount, Instant lastReset, PlanType plan) {
    }
}
