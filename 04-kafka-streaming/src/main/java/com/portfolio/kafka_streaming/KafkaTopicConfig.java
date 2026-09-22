package com.portfolio.kafka_streaming;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	@Bean
	public NewTopic taxComplainceEventsTopic(){
		return TopicBuilder.name("tax-compliance-events").partitions(3).replicas(1).build();
	}
}
