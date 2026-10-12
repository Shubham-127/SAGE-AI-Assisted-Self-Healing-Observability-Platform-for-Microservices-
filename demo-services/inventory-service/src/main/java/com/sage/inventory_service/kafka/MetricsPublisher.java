package com.sage.inventory_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MetricsPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sage.kafka.topics.metrics}")
    private String metricsTopic;

    @Value("${spring.application.name}")
    private String serviceName;

    public MetricsPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(long responseTimeMs, String status) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("service", serviceName);
            event.put("responseTimeMs", responseTimeMs);
            event.put("status", status);
            event.put("timestamp", Instant.now().toString());

            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(metricsTopic, serviceName, json);
        } catch (Exception e) {
            System.err.println("SAGE: failed to publish metric — " + e.getMessage());
        }
    }
}