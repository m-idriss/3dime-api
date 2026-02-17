package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgnoreExtraProperties
public class UserQuota {
    
    // Firestore-friendly field - stores the plan as string
    public String plan;
    public long quotaUsed;
    public long quotaLimit;
    public Timestamp periodStart;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    // Constructor that takes PlanType for backward compatibility
    public UserQuota(PlanType planType, long quotaUsed, long quotaLimit, 
                     Timestamp periodStart, Timestamp createdAt, Timestamp updatedAt) {
        this.plan = planType != null ? planType.name() : PlanType.FREE.name();
        this.quotaUsed = quotaUsed;
        this.quotaLimit = quotaLimit;
        this.periodStart = periodStart;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getter that converts string back to PlanType
    public PlanType getPlanType() {
        return PlanType.fromString(this.plan);
    }

    // Setter that converts PlanType to string
    public void setPlanType(PlanType planType) {
        this.plan = planType != null ? planType.name() : PlanType.FREE.name();
    }
}
