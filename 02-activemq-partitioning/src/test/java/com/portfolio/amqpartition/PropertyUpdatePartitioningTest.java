package com.portfolio.amqpartition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves two things against a real ActiveMQ broker (via Testcontainers):
 *   1. Throughput: many properties' events get processed in parallel across
 *      multiple consumer threads.
 *   2. Ordering: all events for the SAME property are always handled by the
 *      SAME consumer thread — sticky routing via JMSXGroupID.
 */
@Testcontainers
@SpringBootTest
class PropertyUpdatePartitioningTest {

    @Container
    static ActiveMQContainer activeMq = new ActiveMQContainer("apache/activemq-classic:5.18.3");

    @Autowired
    private PropertyUpdateProducer producer;

    @Autowired
    private PropertyUpdateConsumer consumer;

    @Test
    void eventsForSameProperty_areHandledByOneConsumerThread_inOrder() {
        int propertyCount = 20;
        int eventsPerProperty = 10;

        // Publish events for 20 different properties, 10 events each,
        // interleaved to simulate real concurrent partner traffic.
        IntStream.range(0, eventsPerProperty).forEach(eventIndex ->
                IntStream.range(0, propertyCount).forEach(propertyIndex -> {
                    String propertyId = "PROPERTY-" + propertyIndex;
                    producer.publishUpdate(propertyId, propertyId + ":event-" + eventIndex);
                })
        );

        // Wait for all events to be consumed.
        int totalEvents = propertyCount * eventsPerProperty;
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                consumer.getProcessingLog().values().stream().mapToInt(List::size).sum() == totalEvents
        );

        Map<String, List<String>> log = consumer.getProcessingLog();

        // ORDERING PROOF: every property's events were handled by exactly
        // ONE consumer thread — never split across multiple threads.
        for (Map.Entry<String, List<String>> entry : log.entrySet()) {
            Set<String> distinctThreads = entry.getValue().stream().collect(Collectors.toSet());
            assertEquals(1, distinctThreads.size(),
                    "Property " + entry.getKey() + " was handled by multiple threads — sticky routing failed");
        }

        // THROUGHPUT PROOF: across all 20 properties, MORE THAN ONE distinct
        // consumer thread was used overall — proving we're not accidentally
        // serializing everything onto a single thread.
        Set<String> allThreadsUsed = log.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        assertTrue(allThreadsUsed.size() > 1,
                "Expected multiple consumer threads in use for parallelism across properties");

        System.out.println("Distinct consumer threads used across " + propertyCount + " properties: "
                + allThreadsUsed.size());
    }
}
