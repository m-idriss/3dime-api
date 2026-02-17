package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PlanTypeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testPlanTypeCaseInsensitiveDeserialization() throws Exception {
        // Test lowercase
        assertEquals(PlanType.FREE, PlanType.fromString("free"));
        assertEquals(PlanType.PRO, PlanType.fromString("pro"));
        assertEquals(PlanType.UNLIMITED, PlanType.fromString("unlimited"));
        
        // Test uppercase  
        assertEquals(PlanType.FREE, PlanType.fromString("FREE"));
        assertEquals(PlanType.PRO, PlanType.fromString("PRO"));
        assertEquals(PlanType.UNLIMITED, PlanType.fromString("UNLIMITED"));
        
        // Test mixed case
        assertEquals(PlanType.FREE, PlanType.fromString("Free"));
        assertEquals(PlanType.PRO, PlanType.fromString("Pro"));
        
        // Test alternative names
        assertEquals(PlanType.PRO, PlanType.fromString("premium"));
        
        // Test null and unknown values default to FREE
        assertEquals(PlanType.FREE, PlanType.fromString(null));
        assertEquals(PlanType.FREE, PlanType.fromString("unknown"));
        assertEquals(PlanType.FREE, PlanType.fromString(""));
    }

    @Test
    public void testJsonDeserialization() throws Exception {
        String json = "{\"plan\":\"free\"}";
        TestPlan testPlan = objectMapper.readValue(json, TestPlan.class);
        assertEquals(PlanType.FREE, testPlan.plan);
        
        json = "{\"plan\":\"PRO\"}";
        testPlan = objectMapper.readValue(json, TestPlan.class);
        assertEquals(PlanType.PRO, testPlan.plan);
    }

    @Test
    public void testJsonSerialization() throws Exception {
        TestPlan testPlan = new TestPlan(PlanType.FREE);
        String json = objectMapper.writeValueAsString(testPlan);
        assertTrue(json.contains("\"FREE\""));
    }

    public static class TestPlan {
        public PlanType plan;
        
        public TestPlan() {}
        public TestPlan(PlanType plan) { this.plan = plan; }
    }
}