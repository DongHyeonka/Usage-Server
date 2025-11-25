package com.synapse.usage_service_api.events;

import com.synapse.usage_service_api.model.UsageTier;
import java.util.UUID;

/**
 * Event published when a request should be deferred to the queue for later processing.
 */
public record QueueEvent(
        UUID eventId,
        UUID userId,
        UUID workspaceId,
        UsageTier tier,
        String routeId,
        String action,
        String payloadRef,
        Integer priority
) {
}
