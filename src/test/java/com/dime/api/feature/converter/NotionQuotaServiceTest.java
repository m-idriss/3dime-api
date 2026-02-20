package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dime.api.feature.notion.NotionClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        assertDoesNotThrow(() -> notionQuotaService.syncToNotion("user1", 5, PlanType.FREE, Instant.now()));
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

}

