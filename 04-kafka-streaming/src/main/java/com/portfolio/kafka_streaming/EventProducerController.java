package com.portfolio.kafka_streaming;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventProducerController {

	private final KafkaTemplate<String, String> kafkaTemplate;
	
	public EventProducerController(KafkaTemplate<String, String> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}
	@PostMapping("/publish/{businessId}")
	public String publish(@PathVariable String businessId,@RequestParam String  event) {
		kafkaTemplate.send("tax-compliance-events",businessId, event);
		return "Published Event successfulluy for "+businessId +":"+event;
	}
}
