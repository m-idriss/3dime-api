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
public class QuotaReservation {

    public String idempotencyKey;
    public String state;
    public String plan;
    public long quotaLimit;
    public long quotaUsedAfterReservation;
    public int fileCount;
    public int eventCount;
    public String provider;
    public String failureReason;
    public String icsContent;
    public Timestamp periodStart;
    public Timestamp reservedAt;
    public Timestamp completedAt;
    public Timestamp failedAt;
    public Timestamp expiresAt;
    public Timestamp updatedAt;

    public QuotaReservationState getStateType() {
        if (state == null) {
            return QuotaReservationState.RESERVED;
        }
        try {
            return QuotaReservationState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            return QuotaReservationState.RESERVED;
        }
    }

    public void setStateType(QuotaReservationState state) {
        this.state = state != null ? state.name() : QuotaReservationState.RESERVED.name();
    }

    public PlanType getPlanType() {
        return PlanType.fromString(plan);
    }

    public void setPlanType(PlanType plan) {
        this.plan = plan != null ? plan.name() : PlanType.FREE.name();
    }
}
