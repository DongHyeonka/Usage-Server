package com.synapse.usage_service_api.events;

import com.synapse.usage_service_api.model.UsageTier;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event published by the Gateway whenever a request consumes usage quota.
 */
public record UsageEvent(
        UUID eventId,
        UUID userId,
        UUID workspaceId,
        String planCode,
        UsageTier tier,
        String routeId,
        String action,
        Instant usedAt,
        Map<String, Object> payload
) {
}
