package com.sage.order_service.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final RestClient restClient;   // the "phone" from Step 8
    private final String inventoryUrl;     // http://localhost:8080
    private final String paymentUrl;       // http://localhost:8082

    // Spring fills all three automatically when the app starts.
    public OrderController(RestClient restClient,
                           @Value("${sage.downstream.inventory-url}") String inventoryUrl,
                           @Value("${sage.downstream.payment-url}") String paymentUrl) {
        this.restClient = restClient;
        this.inventoryUrl = inventoryUrl;
        this.paymentUrl = paymentUrl;
    }

    // URL: POST /orders/1   (1 = the id of the stock item to order)
    @PostMapping("/{stockId}")
    public ResponseEntity<Map<String, Object>> placeOrder(@PathVariable long stockId) {

        long start = System.currentTimeMillis();            // start a stopwatch
        Map<String, Object> result = new LinkedHashMap<>(); // the answer we will send back, built step by step

        // ---------- CALL 1: phone Inventory ("do we have stock?") ----------
        try {
            Object stock = restClient.get()                       // we are doing an HTTP GET
                    .uri(inventoryUrl + "/stock/" + stockId)      // to http://localhost:8080/stock/1
                    .retrieve()                                   // send it and wait for the reply
                    .body(Object.class);                          // read the reply body
            result.put("inventory", stock);                       // keep the reply in our answer
        } catch (RestClientException e) {
            // Inventory was slow (timeout) or crashed (HTTP 500) -> we stop here and report it
            return fail(result, "inventory-service", e, start);
        }

        // ---------- CALL 2: phone Payment ("charge the customer") ----------
        try {
            Object payment = restClient.post()                    // an HTTP POST this time
                    .uri(paymentUrl + "/payments/order-" + stockId)
                    .retrieve()
                    .body(Object.class);
            result.put("payment", payment);
        } catch (RestClientException e) {
            return fail(result, "payment-service", e, start);
        }

        // Both calls worked, so the order is placed.
        result.put("status", "ORDER_PLACED");
        result.put("totalMs", System.currentTimeMillis() - start);   // total time taken
        return ResponseEntity.ok(result);                            // HTTP 200
    }

    // Small helper that builds the "something went wrong" answer.
    // It says WHICH service failed and how long we waited.
    private ResponseEntity<Map<String, Object>> fail(Map<String, Object> result,
                                                     String service,
                                                     Exception e,
                                                     long start) {
        result.put("status", "ORDER_FAILED");
        result.put("failedService", service);
        result.put("error", e.getClass().getSimpleName());
        result.put("totalMs", System.currentTimeMillis() - start);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);  // HTTP 503
    }
}