package com.sage.payment_service.kafka;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// @Component = "Spring, create one of these and keep it ready for others to use"
@Component
public class MetricsPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate; // Spring's tool for sending to Kafka
    private final String topic;                                // the belt's label: sage.metrics
    private final String serviceName;                          // who we are: order-service

    // Spring fills all three automatically (values come from application.yml)
    public MetricsPublisher(KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${sage.kafka.topics.metrics}") String topic,
                            @Value("${spring.application.name}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.serviceName = serviceName;
    }

    // Called once per request: "this request took X ms and ended with status Y"
    public void publish(long responseTimeMs, String status) {
        try {
            // We build the small JSON note by hand: {"service":"...","responseTimeMs":12,...}
            // %s means "put text here", %d means "put a number here".
            String json = String.format(
                    "{\"service\":\"%s\",\"responseTimeMs\":%d,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                    serviceName, responseTimeMs, status, Instant.now());

            // send(belt label, key, message). The key is the service name.
            kafkaTemplate.send(topic, serviceName, json);
        } catch (Exception e) {
            // If Kafka is down, the service must keep working, so we only print a warning.
            System.err.println("SAGE: could not publish metric: " + e.getMessage());
        }
    }
}