package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
