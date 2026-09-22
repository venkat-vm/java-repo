package com.portfolio.kafka_streaming;


import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

	/***
	 * 
	 * @param records
	 */
	@KafkaListener(topics="tax-compliance-events",groupId="tax-compliance-group",containerFactory = "batchFactory")
	public void consume(List<ConsumerRecord<String, String>> records) {
		 System.out.println("=== Received batch of " + records.size() + " records ===");
	    for(ConsumerRecord<String,String> record:records) {
	    	System.out.println("Consumed event: " + record.value()
	    	+ " | Key (businessId): " + record.key()
	    	+ " | Partition: " + record.partition()
	    	+ " | Thread: " + Thread.currentThread().getName());
	    }
	 }
}
