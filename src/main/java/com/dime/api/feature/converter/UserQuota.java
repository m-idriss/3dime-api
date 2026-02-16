package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuota {
    public PlanType plan;
    public long quotaUsed;
    public long quotaLimit;
    public Timestamp periodStart;
    public Timestamp createdAt;
    public Timestamp updatedAt;
}
