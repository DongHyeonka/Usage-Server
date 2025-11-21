package com.synapse.usage_service.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_daily_aggregate")
public record UsageDailyAggregate(
        @Id Long id,
        @Column("user_id") UUID userId,
        @Column("usage_date") LocalDate usageDate,
        @Column("metric") String metric,
        @Column("count") long count,
        @Column("updated_at") Instant updatedAt
) {
}
