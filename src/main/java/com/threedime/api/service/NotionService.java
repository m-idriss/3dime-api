package com.threedime.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.threedime.api.client.NotionClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class NotionService {

    private static final Logger LOG = Logger.getLogger(NotionService.class);

    @Inject
    @RestClient
    NotionClient notionClient;

    @ConfigProperty(name = "notion.token")
    String token;

    @ConfigProperty(name = "notion.cms.database-id")
    Optional<String> databaseId;

    @ConfigProperty(name = "notion.version")
    String version;

    @Inject
    ObjectMapper objectMapper;

    public Map<String, List<CmsItem>> getCmsContent() {
        if (databaseId.isEmpty() || databaseId.get().trim().isEmpty()) {
            LOG.warn("Notion CMS database ID not configured (notion.cms.database-id). Returning empty content.");
            return new HashMap<>();
        }

        LOG.infof("Fetching CMS content from Notion database: %s", databaseId.get());

        try {
            // Build query payload
            ObjectNode query = objectMapper.createObjectNode();

            // Filter: Name is not empty
            ObjectNode filter = query.putObject("filter");
            filter.put("property", "Name");
            filter.putObject("rich_text").put("is_not_empty", true);

            // Sort: Rank ascending
            ArrayNode sorts = query.putArray("sorts");
            ObjectNode sort = sorts.addObject();
            sort.put("property", "Rank");
            sort.put("direction", "ascending");

            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
            JsonNode response = notionClient.queryDatabase(authToken, version, databaseId.get(), query);

            Map<String, List<CmsItem>> groupedContent = new HashMap<>();

            if (response.has("results") && response.get("results").isArray()) {
                for (JsonNode page : response.get("results")) {
                    JsonNode props = page.get("properties");
                    if (props == null)
                        continue;

                    String name = getTextContent(props.get("Name"));
                    String url = getUrlContent(props.get("URL"));
                    String description = getTextContent(props.get("Description"));
                    long rank = getNumberContent(props.get("Rank"));
                    String category = getSelectContent(props.get("Category"), "Uncategorized");

                    CmsItem item = new CmsItem(name, url, description, rank, category);
                    groupedContent.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
                }
            }

            return groupedContent;

        } catch (org.jboss.resteasy.reactive.ClientWebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf(e, "Notion API error: %s", errorBody);
            throw new RuntimeException("Notion API error: " + errorBody, e);
        } catch (Exception e) {
            LOG.error("Failed to fetch CMS content from Notion", e);
            throw new RuntimeException("Failed to fetch CMS content", e);
        }
    }

    private String getTextContent(JsonNode prop) {
        if (prop != null && prop.has("rich_text") && prop.get("rich_text").isArray()
                && prop.get("rich_text").size() > 0) {
            return prop.get("rich_text").get(0).get("plain_text").asText("");
        }
        // Also handle "title" type which has the same structure
        if (prop != null && prop.has("title") && prop.get("title").isArray() && prop.get("title").size() > 0) {
            return prop.get("title").get(0).get("plain_text").asText("");
        }
        return "";
    }

    private String getUrlContent(JsonNode prop) {
        if (prop != null && prop.has("url")) {
            return prop.get("url").asText("");
        }
        return "";
    }

    private long getNumberContent(JsonNode prop) {
        if (prop != null && prop.has("number")) {
            return prop.get("number").asLong(0);
        }
        return 0;
    }

    private String getSelectContent(JsonNode prop, String defaultValue) {
        if (prop != null && prop.has("select") && !prop.get("select").isNull()) {
            return prop.get("select").get("name").asText(defaultValue);
        }
        return defaultValue;
    }

    public record CmsItem(String name, String url, String description, long rank, String category) {
    }
}
