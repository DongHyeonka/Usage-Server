package com.synapse.usage_service.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_subscription")
public record UserSubscription(
        @Id UUID id,
        @Column("user_id") UUID userId,
        @Column("plan_code") String planCode,
        @Column("current_period_start") Instant currentPeriodStart,
        @Column("current_period_end") Instant currentPeriodEnd,
        @Column("status") SubscriptionStatus status,
        @Column("trial_end") Instant trialEnd,
        @Column("metadata") String metadata,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {
}
