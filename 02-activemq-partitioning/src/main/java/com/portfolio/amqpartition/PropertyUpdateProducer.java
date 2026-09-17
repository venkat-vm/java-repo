package com.portfolio.amqpartition;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Service;

/**
 * Publishes rate/inventory/restriction update events, stamping JMSXGroupID
 * with the property/customer ID.
 *
 * Real-world origin: high-volume concurrent writes to the same property's
 * data were overwhelming DB replica sync (replication lag). The fix was to
 * ensure all events for one property are processed by the SAME consumer,
 * in order, while still parallelizing across DIFFERENT properties. JMS
 * message groups (JMSXGroupID) give us exactly that: per-group ordering
 * with cross-group parallelism, without a broker-side partitioning scheme.
 */
@Service
public class PropertyUpdateProducer {

    private static final String DESTINATION = "property.updates.queue";

    private final JmsTemplate jmsTemplate;

    public PropertyUpdateProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publishUpdate(String propertyId, String payload) {
        jmsTemplate.convertAndSend(DESTINATION, payload, groupIdPostProcessor(propertyId));
    }

    /**
     * Stamps JMSXGroupID = propertyId on the outgoing message header.
     * All messages sharing a group ID are routed to the same consumer for
     * as long as that consumer stays connected — this is the "sticky
     * routing" behavior that preserves per-property ordering.
     */
    private MessagePostProcessor groupIdPostProcessor(String propertyId) {
        return (Message message) -> {
            message.setStringProperty("JMSXGroupID", propertyId);
            return message;
        };
    }
}
