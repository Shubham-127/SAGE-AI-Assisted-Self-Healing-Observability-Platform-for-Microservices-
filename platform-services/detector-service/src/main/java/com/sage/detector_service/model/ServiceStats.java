package com.sage.detector_service.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// The notepad for ONE service: remembers its last 30 readings.
public class ServiceStats {

    // One reading: how long a request took, did it fail, and when it happened.
    public record Event(long responseTimeMs, boolean error, long atMillis) {}

    private final Deque<Event> events = new ArrayDeque<>();

    // "synchronized" = only one thread writes at a time (Kafka thread and web threads share this)
    public synchronized void add(long responseTimeMs, boolean error) {
        events.addLast(new Event(responseTimeMs, error, System.currentTimeMillis()));
        if (events.size() > 30) events.removeFirst();   // keep only the newest 30
    }

    // The last 3 readings that are less than 15 seconds old.
    // Old readings are ignored, so a service that went quiet is not judged on stale data.
    public synchronized List<Event> recent() {
        long cutoff = System.currentTimeMillis() - 15_000;
        List<Event> fresh = new ArrayList<>();
        for (Event e : events) {
            if (e.atMillis() >= cutoff) fresh.add(e);
        }
        int from = Math.max(0, fresh.size() - 3);
        return new ArrayList<>(fresh.subList(from, fresh.size()));
    }

    // All stored response times, for the little chart on the dashboard.
    public synchronized List<Long> history() {
        List<Long> h = new ArrayList<>();
        for (Event e : events) h.add(e.responseTimeMs());
        return h;
    }
}