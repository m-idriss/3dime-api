package com.dime.api.feature.notion;

import com.dime.api.feature.shared.BearerTokenUtil;
import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class NotionService {

    @Inject
    @RestClient
    NotionClient notionClient;

    @ConfigProperty(name = "notion.token")
    String token;

    @ConfigProperty(name = "notion.cms.database-id")
    String databaseId;

    @ConfigProperty(name = "notion.version")
    String version;

    @Inject
    ObjectMapper objectMapper;

    LoadingCache<String, Map<String, List<CmsItem>>> cmsCache;

    @PostConstruct
    void initCaches() {
        cmsCache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofHours(2))
                .expireAfterWrite(Duration.ofHours(24))
                .build(key -> fetchCmsContent());
    }

    public Map<String, List<CmsItem>> getCmsContent() {
        return cmsCache.get("default");
    }

    private Map<String, List<CmsItem>> fetchCmsContent() {
        if (databaseId == null || databaseId.trim().isEmpty()) {
            log.warn("Notion CMS database ID not configured (notion.cms.database-id). Returning empty content.");
            return new HashMap<>();
        }

        log.info("Fetching CMS content from Notion database: {}", databaseId);

        try {
            ObjectNode query = objectMapper.createObjectNode();

            ObjectNode filter = query.putObject("filter");
            filter.put("property", "Name");
            filter.putObject("rich_text").put("is_not_empty", true);

            ArrayNode sorts = query.putArray("sorts");
            ObjectNode sort = sorts.addObject();
            sort.put("property", "Rank");
            sort.put("direction", "ascending");

            String authToken = BearerTokenUtil.ensureBearer(token);
            JsonNode response = notionClient.queryDatabase(authToken, version, databaseId, query);

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
            String errorBody;
            try {
                errorBody = e.getResponse().readEntity(String.class);
            } catch (Exception readEx) {
                errorBody = "Unable to read error response";
            }
            log.error("Notion API error: {} - Status: {}", errorBody, e.getResponse().getStatus(), e);
            throw new ExternalServiceException("Notion",
                    "Notion API returned error (Status " + e.getResponse().getStatus() + "): " + errorBody, e);
        } catch (Exception e) {
            log.error("Failed to fetch CMS content from Notion", e);
            throw new ExternalServiceException("Notion",
                    "Failed to fetch CMS content from Notion: " + e.getMessage(), e);
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
