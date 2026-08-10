package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@IgnoreExtraProperties
public class QuotaSubject {
    public String subjectType;
    public long usageCount;
    public long quotaLimit;
    public Timestamp periodStart;
    public Timestamp expiresAt;
    public Timestamp createdAt;
    public Timestamp updatedAt;
    public List<String> accountHashes = new ArrayList<>();
}
