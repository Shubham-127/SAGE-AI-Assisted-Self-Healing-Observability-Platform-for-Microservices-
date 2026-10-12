package com.sage.detector_service.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Places a test order every 2 seconds, so metrics keep flowing during the demo.
@Component
public class TrafficGenerator {

    private final RestClient http = RestClient.create();

    @Scheduled(fixedDelay = 2000)
    public void sendOrder() {
        try {
            http.post().uri("http://localhost:8081/orders/1").retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // Order answers 503 while a fault is active. That is expected, not a problem.
        }
    }
}