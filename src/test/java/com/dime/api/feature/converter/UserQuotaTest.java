package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest 
public class UserQuotaTest {

    @Test
    public void testPlanTypeConversionFromString() {
        UserQuota quota = new UserQuota();
        
        // Test case-insensitive conversion
        quota.plan = "free";
        assertEquals(PlanType.FREE, quota.getPlanType());
        
        quota.plan = "FREE";
        assertEquals(PlanType.FREE, quota.getPlanType());
        
        quota.plan = "pro";
        assertEquals(PlanType.PRO, quota.getPlanType());
        
        quota.plan = "PRO";
        assertEquals(PlanType.PRO, quota.getPlanType());
        
        quota.plan = "unlimited";
        assertEquals(PlanType.UNLIMITED, quota.getPlanType());
        
        // Test unknown value defaults to FREE
        quota.plan = "unknown";
        assertEquals(PlanType.FREE, quota.getPlanType());
        
        // Test null defaults to FREE
        quota.plan = null;
        assertEquals(PlanType.FREE, quota.getPlanType());
    }

    @Test
    public void testPlanTypeConversionToString() {
        UserQuota quota = new UserQuota();
        
        quota.setPlanType(PlanType.FREE);
        assertEquals("FREE", quota.plan);
        
        quota.setPlanType(PlanType.PRO);
        assertEquals("PRO", quota.plan);
        
        quota.setPlanType(PlanType.UNLIMITED);
        assertEquals("UNLIMITED", quota.plan);
        
        // Test null safety
        quota.setPlanType(null);
        assertEquals("FREE", quota.plan);
    }

    @Test
    public void testConstructorWithPlanType() {
        Timestamp now = Timestamp.now();
        UserQuota quota = new UserQuota(PlanType.PRO, 5, 100, now, now, now);
        
        assertEquals("PRO", quota.plan);
        assertEquals(PlanType.PRO, quota.getPlanType());
        assertEquals(5, quota.quotaUsed);
        assertEquals(100, quota.quotaLimit);
    }

    @Test
    public void testBackwardCompatibilityWithFirestore() {
        // Simulate Firestore deserialization where plan field contains lowercase string
        UserQuota quota = new UserQuota();
        quota.plan = "free";  // This is what Firestore might store
        quota.quotaUsed = 3;
        quota.quotaLimit = 10;
        
        // Should convert correctly when accessing via getPlanType()
        assertEquals(PlanType.FREE, quota.getPlanType());
        assertEquals(3, quota.quotaUsed);
        assertEquals(10, quota.quotaLimit);
    }
}