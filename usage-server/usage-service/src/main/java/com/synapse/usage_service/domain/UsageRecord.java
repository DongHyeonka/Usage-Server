package com.synapse.usage_service.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_record")
public record UsageRecord(
        @Id UUID id,
        @Column("event_id") UUID eventId,
        @Column("user_id") UUID userId,
        @Column("workspace_id") UUID workspaceId,
        @Column("plan_code") String planCode,
        @Column("tier") UsageTier tier,
        @Column("route_id") String routeId,
        @Column("action") String action,
        @Column("used_at") Instant usedAt,
        @Column("payload") String payload,
        @Column("created_at") Instant createdAt
) {
}
