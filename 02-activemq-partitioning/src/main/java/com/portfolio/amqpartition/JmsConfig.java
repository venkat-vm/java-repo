package com.portfolio.amqpartition;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * Central configuration for every throughput/reliability lever used to tune
 * this pipeline in production. Each setting below maps to a specific,
 * real-world tuning decision.
 */
@Configuration
@EnableJms
public class JmsConfig {

    private static final String BROKER_URL = "tcp://localhost:61616";

    /**
     * Raw connection factory with prefetch and redelivery (retry) policy.
     */
    @Bean
    public ActiveMQConnectionFactory activeMQConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);

        // PREFETCH: how many messages a consumer pulls in a batch before
        // acknowledging, rather than one round-trip per message. Higher =
        // fewer network round-trips = higher throughput, but too high risks
        // one slow consumer hoarding messages while others sit idle.
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(50);
        factory.setPrefetchPolicy(prefetchPolicy);

        // RETRIES: redelivery policy for transient failures (e.g. a
        // downstream call inside the listener throws). Exponential backoff
        // avoids hammering a struggling downstream dependency.
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setMaximumRedeliveries(5);
        redeliveryPolicy.setInitialRedeliveryDelay(1000);
        redeliveryPolicy.setBackOffMultiplier(2.0);
        redeliveryPolicy.setUseExponentialBackOff(true);
        factory.setRedeliveryPolicy(redeliveryPolicy);

        return factory;
    }

    /**
     * CONNECTION POOLING: reuses a fixed pool of connections/sessions rather
     * than creating a new JMS connection per operation — creating a raw JMS
     * connection is expensive; pooling amortizes that cost under load.
     */
    @Bean
    public ConnectionFactory pooledConnectionFactory(ActiveMQConnectionFactory activeMQConnectionFactory) {
        PooledConnectionFactory pooledFactory = new PooledConnectionFactory();
        pooledFactory.setConnectionFactory(activeMQConnectionFactory);
        pooledFactory.setMaxConnections(10);
        pooledFactory.setIdleTimeout(30000);
        return pooledFactory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory pooledConnectionFactory) {
        return new JmsTemplate(pooledConnectionFactory);
    }

    /**
     * Listener container factory for the property-update queue — this is
     * where concurrency and ack-mode are actually applied per listener.
     */
    @Bean
    public DefaultJmsListenerContainerFactory propertyUpdateListenerFactory(
            ConnectionFactory pooledConnectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(pooledConnectionFactory);

        // CONCURRENCY: min-max concurrent listener threads. This is the
        // single biggest throughput lever — each thread independently pulls
        // and processes messages, and JMSXGroupID ensures per-property
        // ordering is preserved even with many threads running.
        factory.setConcurrency("5-10");

        // ACK MODE: AUTO_ACKNOWLEDGE acknowledges every message individually
        // (safer, slower). For high-volume, replayable event types where an
        // occasional duplicate on failure is acceptable downstream (because
        // processing is idempotent), DUPS_OK_ACKNOWLEDGE allows batched,
        // lazy acknowledgment for higher throughput.
        factory.setSessionAcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE);

        return factory;
    }
}
