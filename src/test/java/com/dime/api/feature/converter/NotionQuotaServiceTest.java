package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dime.api.feature.notion.NotionClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
public class NotionQuotaServiceTest {
    NotionQuotaService notionQuotaService;
    NotionClient notionClient;
    ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        notionClient = mock(NotionClient.class);
        objectMapper = new ObjectMapper();
        notionQuotaService = new NotionQuotaService();
        notionQuotaService.notionClient = notionClient;
        notionQuotaService.objectMapper = objectMapper;
        notionQuotaService.token = "dummy-token";
        notionQuotaService.version = "2022-06-28";
        notionQuotaService.quotaDbId = Optional.of("dummy-db-id");
    }

    @Test
    public void testSyncToNotionHandlesDisabled() {
        notionQuotaService.quotaDbId = Optional.empty();
        // Should not throw
        assertDoesNotThrow(() -> notionQuotaService.syncToNotion("user1", 5, PlanType.FREE, Instant.now(), null));
    }

    @Test
    public void testFetchAllFromNotionHandlesDisabled() {
        notionQuotaService.quotaDbId = Optional.empty();
        assertTrue(notionQuotaService.fetchAllFromNotion().isEmpty());
    }

    @Test
    public void testDeleteFromNotionHandlesDisabled() {
        notionQuotaService.quotaDbId = Optional.empty();
        assertDoesNotThrow(() -> notionQuotaService.deleteFromNotion("user1"));
    }

    @Test
    public void testAddTitleProperty() {
        ObjectNode node = objectMapper.createObjectNode();
        notionQuotaService.addTitleProperty(node, "User ID", "user1");
        assertTrue(node.has("User ID"));
    }

    @Test
    public void testFetchAllFromNotionPaginates() {
        ObjectNode page1 = buildPageResponse("user1", 3, "FREE", "2026-01-01T00:00:00Z", true, "cursor-abc");
        ObjectNode page2 = buildPageResponse("user2", 7, "PRO", "2026-02-01T00:00:00Z", false, null);

        when(notionClient.queryDatabase(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    JsonNode body = objectMapper.convertValue(inv.getArgument(3), JsonNode.class);
                    if (body.has("start_cursor") && "cursor-abc".equals(body.get("start_cursor").asText())) {
                        return page2;
                    }
                    return page1;
                });

        List<NotionQuotaService.QuotaData> results = notionQuotaService.fetchAllFromNotion();

        assertEquals(2, results.size());
        assertEquals("user1", results.get(0).userId());
        assertEquals("user1@example.com", results.get(0).email());
        assertEquals("user2", results.get(1).userId());
        assertEquals("user2@example.com", results.get(1).email());
        verify(notionClient, times(2)).queryDatabase(any(), any(), any(), any());
    }

    private ObjectNode buildPageResponse(String userId, long usageCount, String plan, String lastReset,
            boolean hasMore, String nextCursor) {
        String email = userId + "@example.com";
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode results = response.putArray("results");
        ObjectNode page = results.addObject();
        ObjectNode props = page.putObject("properties");

        notionQuotaService.addTitleProperty(props, "User ID", userId);
        props.putObject("Usage Count").put("number", usageCount);
        props.putObject("Plan").putObject("select").put("name", plan);
        props.putObject("Last Reset").putObject("date").put("start", lastReset);
        notionQuotaService.addRichTextProperty(props, "Email", email);

        response.put("has_more", hasMore);
        if (nextCursor != null) {
            response.put("next_cursor", nextCursor);
        }
        return response;
    }

}

