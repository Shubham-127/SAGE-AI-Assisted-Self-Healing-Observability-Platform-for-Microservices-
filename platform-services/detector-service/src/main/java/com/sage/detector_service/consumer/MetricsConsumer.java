package com.sage.detector_service.consumer;

import com.sage.detector_service.service.DetectorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Component;

// Reads sage.metrics WITHOUT a consumer group, so it never needs Kafka's group coordinator.
// It starts at the newest message, so old messages cannot pollute the live stats.
@Component
public class MetricsConsumer {

    private static final String TOPIC = "sage.metrics";

    private final DetectorService detector;
    private volatile boolean running = true;

    public MetricsConsumer(DetectorService detector) {
        this.detector = detector;
    }

    // Starts one background thread when the application starts.
    @PostConstruct
    public void start() {
        Thread t = new Thread(this::loop, "sage-metrics-consumer");
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
    }

    private void loop() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("enable.auto.commit", "false");   // no group, so no offset commits

        while (running) {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                List<TopicPartition> partitions = consumer.partitionsFor(TOPIC).stream()
                        .map(info -> new TopicPartition(info.topic(), info.partition()))
                        .toList();
                consumer.assign(partitions);      // read the partitions directly
                consumer.seekToEnd(partitions);   // only NEW messages from now on
                System.out.println("SAGE detector is listening on " + TOPIC + " " + partitions);

                while (running) {
                    for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                        handle(record.value());
                    }
                }
            } catch (Exception e) {
                System.out.println("SAGE consumer error, retrying in 3s: " + e);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void handle(String json) {
        try {
            String service = read(json, "service");
            long responseTimeMs = Long.parseLong(read(json, "responseTimeMs"));
            boolean error = "ERROR".equals(read(json, "status"));
            System.out.println("SAGE detector got: " + service + " " + responseTimeMs + "ms " + (error ? "ERROR" : "OK"));
            detector.record(service, responseTimeMs, error);
        } catch (Exception e) {
            System.out.println("SAGE skipped a bad message: " + json);
        }
    }

    // Finds "key":"value" or "key":123 in our flat JSON note.
    private String read(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker) + marker.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end);
    }
}