package com.threedime.api.feature.converter;

import com.google.cloud.Timestamp;

public class UserQuota {
    public PlanType plan;
    public long quotaUsed;
    public long quotaLimit;
    public Timestamp periodStart;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public UserQuota() {
    }

    public UserQuota(PlanType plan, long quotaUsed, long quotaLimit, Timestamp periodStart, Timestamp createdAt,
            Timestamp updatedAt) {
        this.plan = plan;
        this.quotaUsed = quotaUsed;
        this.quotaLimit = quotaLimit;
        this.periodStart = periodStart;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
