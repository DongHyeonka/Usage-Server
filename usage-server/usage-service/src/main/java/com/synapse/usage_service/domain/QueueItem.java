package com.synapse.usage_service.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("queue_item")
public record QueueItem(
        @Id UUID id,
        @Column("user_id") UUID userId,
        @Column("workspace_id") UUID workspaceId,
        @Column("tier") UsageTier tier,
        @Column("route_id") String routeId,
        @Column("action") String action,
        @Column("payload_ref") String payloadRef,
        @Column("status") QueueItemStatus status,
        @Column("priority") int priority,
        @Column("queued_at") Instant queuedAt,
        @Column("started_at") Instant startedAt,
        @Column("completed_at") Instant completedAt,
        @Column("last_error") String lastError
) {
}
