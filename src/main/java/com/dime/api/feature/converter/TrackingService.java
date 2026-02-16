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
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class TrackingService {

    private static final Logger LOG = Logger.getLogger(TrackingService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

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

    private boolean isEnabled() {
        return notionToken.isPresent() && trackingDbId.isPresent();
    }

    public void logConversion(String userId, int fileCount, String domain, int eventCount, long duration) {
        logEvent("conversion", userId, "Success", fileCount, eventCount, duration, null, domain);
    }

    public void logConversionError(String userId, int fileCount, String errorMessage, long duration, String domain) {
        logEvent("conversion", userId, "Error", fileCount, 0, duration, errorMessage, domain);
    }

    public void logQuotaExceeded(String userId, int usageCount, int limit, String plan, String domain) {
        String errorMessage = String.format("Quota exceeded: %d/%d (plan: %s)", usageCount, limit, plan);
        logEvent("quota_exceeded", userId, "Error", usageCount, limit, 0, errorMessage, domain);
    }

    private void logEvent(String action, String userId, String status, int fileCount, int eventCount, long duration,
            String errorMessage, String domain) {
        if (!isEnabled()) {
            LOG.debug("Tracking disabled: Notion token or DB ID missing");
            return;
        }

        try {
            ObjectNode parent = objectMapper.createObjectNode();
            parent.put("type", "database_id");
            parent.put("database_id", trackingDbId.get());

            ObjectNode properties = objectMapper.createObjectNode();
            addTitleProperty(properties, "Action", action);
            addRichTextProperty(properties, "User ID", userId);
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

            ObjectNode page = objectMapper.createObjectNode();
            page.set("parent", parent);
            page.set("properties", properties);

            notionClient.createPage("Bearer " + notionToken.get(), notionVersion, page);
            LOG.infof("Logged usage event: %s for user %s", action, userId);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to log usage event to Notion: %s", e.getMessage());
        }
    }

    public Statistics getStatistics() {
        if (!isEnabled()) {
            return new Statistics(0, 0);
        }

        try {
            ObjectNode filter = objectMapper.createObjectNode();
            ArrayNode and = filter.putObject("filter").putArray("and");

            ObjectNode actionFilter = and.addObject();
            actionFilter.put("property", "Action");
            actionFilter.putObject("title").put("equals", "conversion");

            ObjectNode statusFilter = and.addObject();
            statusFilter.put("property", "Status");
            statusFilter.putObject("select").put("equals", "Success");

            JsonNode response = notionClient.queryDatabase("Bearer " + notionToken.get(), notionVersion,
                    trackingDbId.get(), filter);

            int totalFileCount = 0;
            int totalEventCount = 0;

            if (response.has("results")) {
                for (JsonNode page : response.get("results")) {
                    JsonNode props = page.get("properties");
                    if (props != null) {
                        if (props.has("File Count")) {
                            totalFileCount += props.get("File Count").get("number").asInt(0);
                        }
                        if (props.has("Event Count")) {
                            totalEventCount += props.get("Event Count").get("number").asInt(0);
                        }
                    }
                }
            }

            return new Statistics(totalFileCount, totalEventCount);

        } catch (Exception e) {
            LOG.error("Failed to fetch statistics from Notion", e);
            return new Statistics(0, 0);
        }
    }

    public record Statistics(int fileCount, int eventCount) {
    }

    private void addTitleProperty(ObjectNode properties, String name, String content) {
        ObjectNode title = properties.putObject(name).putArray("title").addObject();
        title.putObject("text").put("content", content);
    }

    private void addRichTextProperty(ObjectNode properties, String name, String content) {
        ObjectNode richText = properties.putObject(name).putArray("rich_text").addObject();
        richText.putObject("text").put("content", content);
    }

    private void addDateProperty(ObjectNode properties, String name, String date) {
        properties.putObject(name).putObject("date").put("start", date);
    }

    private void addSelectProperty(ObjectNode properties, String name, String option) {
        properties.putObject(name).putObject("select").put("name", option);
    }

    private void addNumberProperty(ObjectNode properties, String name, Number value) {
        properties.putObject(name).put("number", value.doubleValue());
    }
}
