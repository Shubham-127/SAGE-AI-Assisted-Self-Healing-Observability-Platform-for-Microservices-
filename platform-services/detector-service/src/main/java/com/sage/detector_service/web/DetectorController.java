package com.sage.detector_service.web;

import com.sage.detector_service.model.Incident;
import com.sage.detector_service.service.DetectorService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // lets the Flutter app (even in Chrome) call us. Fine for a prototype.
public class DetectorController {

    // Only Inventory and Payment have a fault switch.
    private static final Map<String, Integer> FAULT_PORTS =
            Map.of("inventory-service", 8080, "payment-service", 8082);

    private final DetectorService detector;
    private final RestClient http = RestClient.create();

    public DetectorController(DetectorService detector) {
        this.detector = detector;
    }

    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        return detector.services();
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() {
        return detector.incidents();
    }

    // The dashboard asks us to break a service. We forward it to that service's own switch.
    @PostMapping("/faults/{service}/inject")
    public ResponseEntity<String> inject(@PathVariable String service, @RequestParam String type) {
        return forward(service, "/admin/fault/inject?type=" + type);
    }

    // The dashboard's "approve fix" button. In the full design, the Kubernetes operator does this.
    @PostMapping("/faults/{service}/clear")
    public ResponseEntity<String> clear(@PathVariable String service) {
        return forward(service, "/admin/fault/clear");
    }

    private ResponseEntity<String> forward(String service, String path) {
        Integer port = FAULT_PORTS.get(service);
        if (port == null) return ResponseEntity.badRequest().body("No fault switch on " + service);
        try {
            http.post().uri("http://localhost:" + port + path).retrieve().toBodilessEntity();
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.status(502).body("Could not reach " + service);
        }
    }
}