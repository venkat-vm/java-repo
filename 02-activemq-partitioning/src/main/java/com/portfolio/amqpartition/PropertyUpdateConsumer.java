package com.portfolio.amqpartition;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consumer side of the property-update flow.
 *
 * `concurrency = "5-10"` is the primary throughput lever: Spring will scale
 * between 5 and 10 concurrent listener threads based on load. Combined with
 * JMSXGroupID on the producer side, this gives us parallelism ACROSS
 * properties while preserving strict ordering WITHIN a property — each
 * group is pinned to one consumer thread at a time.
 */
@Component
public class PropertyUpdateConsumer {

    // Tracks which "consumer" (simulated) handled events for each property,
    // to demonstrate group-sticky routing in the accompanying test.
    private final Map<String, List<String>> processingLog = new ConcurrentHashMap<>();

    @JmsListener(
            destination = "property.updates.queue",
            concurrency = "5-10", // min-max concurrent consumer threads — the biggest throughput lever
            containerFactory = "propertyUpdateListenerFactory"
    )
    public void onPropertyUpdate(String payload) {
        String threadName = Thread.currentThread().getName();
        String propertyId = extractPropertyId(payload);
        processingLog.computeIfAbsent(propertyId, k -> new CopyOnWriteArrayList<>()).add(threadName);
        // Real processing (rate/inventory/restriction update) would happen here.
    }

    private String extractPropertyId(String payload) {
        // Demo payload format: "propertyId:eventData"
        return payload.split(":", 2)[0];
    }

    public Map<String, List<String>> getProcessingLog() {
        return processingLog;
    }
}
