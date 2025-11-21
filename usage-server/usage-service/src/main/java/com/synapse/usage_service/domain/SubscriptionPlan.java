package com.synapse.usage_service.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("subscription_plan")
public record SubscriptionPlan(
        @Id UUID id,
        @Column("plan_code") String planCode,
        @Column("display_name") String displayName,
        @Column("daily_quota") Integer dailyQuota,
        @Column("monthly_quota") Integer monthlyQuota,
        @Column("burst_quota") String burstQuota,
        @Column("allow_queue_overflow") boolean allowQueueOverflow,
        @Column("metadata") String metadata,
        @Column("updated_at") Instant updatedAt
) {
}
