package com.synapse.usage_service.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_monthly_aggregate")
public record UsageMonthlyAggregate(
        @Id Long id,
        @Column("user_id") UUID userId,
        @Column("year_month") String yearMonth,
        @Column("metric") String metric,
        @Column("count") long count,
        @Column("updated_at") Instant updatedAt
) {
}
