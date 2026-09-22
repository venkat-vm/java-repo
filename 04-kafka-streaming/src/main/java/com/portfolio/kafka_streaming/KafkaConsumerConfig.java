package com.portfolio.kafka_streaming;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@Configuration
public class KafkaConsumerConfig {

	 /**
     * Consumer factory with max.poll.records tuned for batch processing —
     * this controls how many records Kafka hands back in a single poll,
     * which becomes the batch size your listener method receives.
     */
	@Bean
	public ConsumerFactory<String, String> consumerFactory(){
		Map<String,Object> properties = new HashMap<>();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, "tax-compliance-group");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
		return new DefaultKafkaConsumerFactory<String, String>(properties);
	}
	
	/**
     * The batch-mode container factory — referenced by name
     * ("batchFactory") from the @KafkaListener annotation.
     */
	@Bean(name ="batchFactory")
	public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(ConsumerFactory<String,String> consumerFactory){
		ConcurrentKafkaListenerContainerFactory<String, String> concurrentKafkaListenerContainerFactory = new ConcurrentKafkaListenerContainerFactory<String, String>();
		concurrentKafkaListenerContainerFactory.setConsumerFactory(consumerFactory);
		concurrentKafkaListenerContainerFactory.setBatchListener(true);
		return concurrentKafkaListenerContainerFactory;
	}
}
