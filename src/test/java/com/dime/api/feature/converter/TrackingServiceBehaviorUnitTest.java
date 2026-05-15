package com.dime.api.feature.converter;

import com.dime.api.feature.notion.NotionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingServiceBehaviorUnitTest {

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
    void getStatistics_whenNotionHasMultiplePages_aggregatesAllPages() {
        ObjectNode firstPage = buildResponse(2, 3, true, "cursor-1");
        ObjectNode secondPage = buildResponse(4, 5, false, null);

        when(mockNotionClient.queryDatabase(any(), any(), any(), any()))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        TrackingService.Statistics stats = service.getStatistics();
        assertEquals(6, stats.fileCount());
        assertEquals(8, stats.eventCount());

        ArgumentCaptor<Object> queryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mockNotionClient, times(2)).queryDatabase(any(), any(), any(), queryCaptor.capture());
        JsonNode firstQuery = (JsonNode) queryCaptor.getAllValues().get(0);
        JsonNode secondQuery = (JsonNode) queryCaptor.getAllValues().get(1);

        assertFalse(firstQuery.has("start_cursor"));
        assertEquals("cursor-1", secondQuery.get("start_cursor").asText());
    }

    @Test
    void fetchStatistics_whenNotionThrows_returnsCachedValue() throws Exception {
        service.statisticsCache.put("default", new TrackingService.Statistics(11, 22));
        when(mockNotionClient.queryDatabase(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Notion unavailable"));

        TrackingService.Statistics stats = invokeFetchStatistics(service);
        assertEquals(11, stats.fileCount());
        assertEquals(22, stats.eventCount());
    }

    private ObjectNode buildResponse(int fileCount, int eventCount, boolean hasMore, String nextCursor) {
        ObjectNode response = service.objectMapper.createObjectNode();
        ArrayNode results = response.putArray("results");

        ObjectNode page = results.addObject();
        ObjectNode properties = page.putObject("properties");
        properties.putObject("File Count").put("number", fileCount);
        properties.putObject("Event Count").put("number", eventCount);

        response.put("has_more", hasMore);
        if (nextCursor == null) {
            response.putNull("next_cursor");
        } else {
            response.put("next_cursor", nextCursor);
        }
        return response;
    }

    private TrackingService.Statistics invokeFetchStatistics(TrackingService trackingService) throws Exception {
        Method fetchMethod = TrackingService.class.getDeclaredMethod("fetchStatistics");
        fetchMethod.setAccessible(true);
        return (TrackingService.Statistics) fetchMethod.invoke(trackingService);
    }
}

