package com.sage.detector_service.service;

import com.sage.detector_service.model.Incident;
import com.sage.detector_service.model.ServiceStats;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DetectorService {

    // WHO CALLS WHOM. Order calls Inventory and Payment. This is how we tell cause from symptom.
    private static final Map<String, List<String>> DEPENDS_ON = new LinkedHashMap<>();
    private static final Map<String, Integer> PORTS = Map.of(
            "order-service", 8081, "inventory-service", 8080, "payment-service", 8082);
    static {
        DEPENDS_ON.put("order-service", List.of("inventory-service", "payment-service"));
        DEPENDS_ON.put("inventory-service", List.of());
        DEPENDS_ON.put("payment-service", List.of());
    }

    // A culprit must stay the culprit for 10 seconds before we open an incident.
    // Why: when Inventory is slow, Order shows errors FIRST (after 2s), and Inventory's own
    // slow reading only arrives after 5-8s. Waiting avoids blaming Order by mistake.
    private static final long CONFIRM_MS = 10_000;

    private final Map<String, ServiceStats> stats = new ConcurrentHashMap<>();
    private final Map<String, Long> rootSince = new ConcurrentHashMap<>();
    private final List<Incident> incidents = new CopyOnWriteArrayList<>();
    private int counter = 0;

    // A quick summary of one service's recent readings.
    private record Snap(int n, double avgMs, double errorPct, boolean anomalous) {}

    // Called by the Kafka consumer for every note that arrives.
    public void record(String service, long responseTimeMs, boolean error) {
        stats.computeIfAbsent(service, k -> new ServiceStats()).add(responseTimeMs, error);
    }

    // RULE: a service is "anomalous" if its recent average is 1 second or more,
    // or at least half of its recent requests failed.
    private Snap snap(String service) {
        ServiceStats st = stats.get(service);
        List<ServiceStats.Event> r = (st == null) ? List.of() : st.recent();
        if (r.isEmpty()) return new Snap(0, 0, 0, false);
        double avg = r.stream().mapToLong(ServiceStats.Event::responseTimeMs).average().orElse(0);
        double err = 100.0 * r.stream().filter(ServiceStats.Event::error).count() / r.size();
        return new Snap(r.size(), avg, err, avg >= 1000 || err >= 50);
    }

    // Runs by itself every second: find the real culprit and manage incidents.
    @Scheduled(fixedDelay = 1000)
    public synchronized void analyze() {
        Set<String> anomalous = new HashSet<>();
        for (String s : DEPENDS_ON.keySet()) {
            if (snap(s).anomalous()) anomalous.add(s);
        }

        // THE KEY IDEA: if a service is anomalous AND something it calls is also anomalous,
        // it is only a symptom. A "root" is anomalous with NO broken dependency.
        Set<String> roots = new HashSet<>();
        for (String s : anomalous) {
            boolean dependencyBroken = DEPENDS_ON.get(s).stream().anyMatch(anomalous::contains);
            if (!dependencyBroken) roots.add(s);
        }

        long now = System.currentTimeMillis();
        rootSince.keySet().removeIf(s -> !roots.contains(s));   // forget services that are no longer culprits

        for (String root : roots) {
            rootSince.putIfAbsent(root, now);
            boolean alreadyOpen = incidents.stream()
                    .anyMatch(i -> i.rootService.equals(root) && i.status.equals("OPEN"));
            if (!alreadyOpen && now - rootSince.get(root) >= CONFIRM_MS) {
                openIncident(root, anomalous);
            }
        }

        // Close incidents whose culprit has recovered.
        for (Incident i : incidents) {
            if (i.status.equals("OPEN") && !roots.contains(i.rootService)) {
                i.status = "RESOLVED";
                i.resolvedAt = Instant.now().toString();
            }
        }
    }

    private void openIncident(String root, Set<String> anomalous) {
        Snap sn = snap(root);
        List<String> affected = new ArrayList<>();
        for (String s : anomalous) {
            if (!s.equals(root) && DEPENDS_ON.get(s).contains(root)) affected.add(s);
        }
        List<String> evidence = new ArrayList<>();
        evidence.add(String.format("%s: average %.0f ms and %.0f%% errors over its last %d requests",
                root, sn.avgMs(), sn.errorPct(), sn.n()));
        for (String a : affected) {
            evidence.add(a + " is also anomalous, but it calls " + root + ": a symptom, not the cause");
        }

        Incident inc = new Incident();
        inc.id = "INC-" + String.format("%03d", ++counter);
        inc.rootService = root;
        inc.affected = affected;
        inc.evidence = evidence;
        inc.suggestedAction = sn.errorPct() >= 50 ? "RESTART_POD" : "SCALE_UP";
        inc.status = "OPEN";
        inc.openedAt = Instant.now().toString();
        incidents.add(0, inc);   // newest first
    }

    // What the dashboard shows for each service.
    public List<Map<String, Object>> services() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String s : DEPENDS_ON.keySet()) {
            Snap sn = snap(s);
            boolean dependencyBroken = sn.anomalous()
                    && DEPENDS_ON.get(s).stream().anyMatch(d -> snap(d).anomalous());
            String health = sn.n() == 0 ? "idle"
                    : !sn.anomalous() ? "healthy"
                    : sn.errorPct() >= 50 ? "down" : "degraded";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s);
            m.put("port", PORTS.get(s));
            m.put("health", health);
            m.put("avgMs", Math.round(sn.avgMs()));
            m.put("errorPct", Math.round(sn.errorPct()));
            m.put("role", !sn.anomalous() ? "ok" : dependencyBroken ? "symptom" : "root");
            m.put("dependsOn", DEPENDS_ON.get(s));
            m.put("history", stats.containsKey(s) ? stats.get(s).history() : List.of());
            out.add(m);
        }
        return out;
    }

    public List<Incident> incidents() {
        return incidents;
    }
}