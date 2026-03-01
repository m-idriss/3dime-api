package com.dime.api.feature.converter;

import com.dime.api.feature.notion.NotionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
public class TrackingServiceTest {
    private TrackingService trackingService;

    @BeforeEach
    public void setup() {
        trackingService = new TrackingService();
    }

    @Test
    public void testAddTitleProperty() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        trackingService.addTitleProperty(node, "title", "Test Title");
        assertTrue(node.has("title"));
    }

    @Test
    public void testAddRichTextProperty() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        trackingService.addRichTextProperty(node, "desc", "Description");
        assertTrue(node.has("desc"));
    }

    @Test
    public void testAddDateProperty() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        trackingService.addDateProperty(node, "date", "2026-02-20");
        assertTrue(node.has("date"));
    }

    @Test
    public void testAddSelectProperty() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        trackingService.addSelectProperty(node, "option", "A");
        assertTrue(node.has("option"));
    }

    @Test
    public void testAddNumberProperty() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        trackingService.addNumberProperty(node, "num", 42);
        assertTrue(node.has("num"));
    }

    @Test
    public void testStatisticsRecord() {
        TrackingService.Statistics stats = new TrackingService.Statistics(5, 10);
        assertEquals(5, stats.fileCount());
        assertEquals(10, stats.eventCount());
    }

    // --- Behavior tests (plain unit tests, no Quarkus context needed) ---

    @Nested
    class BehaviorTest {

        private TrackingService service;
        private NotionClient mockNotionClient;

        @BeforeEach
        void setup() {
            service = new TrackingService();
            mockNotionClient = mock(NotionClient.class);
            service.notionClient = mockNotionClient;
            service.objectMapper = new ObjectMapper();
            service.notionToken = Optional.of("test-token");
            service.trackingDbId = Optional.of("test-db-id");
            service.notionVersion = "2022-02-22";
            service.assignedUserId = Optional.empty();
            service.initCaches();
        }

        @Test
        void logConversion_whenEnabled_callsNotionClient() {
            service.logConversion("user1", 2, "test.com", 3, 500L);
            verify(mockNotionClient, times(1)).createPage(any(), any(), any());
        }

        @Test
        void logConversion_whenDisabled_neverCallsNotionClient() {
            service.notionToken = Optional.empty();
            service.logConversion("user1", 2, "test.com", 3, 500L);
            verify(mockNotionClient, never()).createPage(any(), any(), any());
        }

        @Test
        void logConversionError_longMessage_isTruncatedAndDoesNotThrow() {
            String longMessage = "e".repeat(2001);
            assertDoesNotThrow(() -> service.logConversionError("user1", 1, longMessage, 100L, "test.com"));
            verify(mockNotionClient, times(1)).createPage(any(), any(), any());
        }

        @Test
        void logQuotaExceeded_whenEnabled_callsNotionClient() {
            service.logQuotaExceeded("user1", 10, 10, "FREE", "test.com");
            verify(mockNotionClient, times(1)).createPage(any(), any(), any());
        }

        @Test
        void getStatistics_whenDisabled_returnsZeros() {
            service.notionToken = Optional.empty();
            TrackingService.Statistics stats = service.getStatistics();
            assertEquals(0, stats.fileCount());
            assertEquals(0, stats.eventCount());
        }

        @Test
        void getStatistics_whenNotionThrows_returnsZeros() {
            when(mockNotionClient.queryDatabase(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Notion unavailable"));
            TrackingService.Statistics stats = service.getStatistics();
            assertEquals(0, stats.fileCount());
            assertEquals(0, stats.eventCount());
        }
    }
}
