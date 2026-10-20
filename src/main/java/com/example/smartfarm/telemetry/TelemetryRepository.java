package com.example.smartfarm.telemetry;

import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
public class TelemetryRepository {

    private static final int MAX_PER_KEY = 1_000;

    private final Map<String, Deque<TelemetryRecord>> store = new ConcurrentHashMap<>();

    public void save(TelemetryRecord record) {
        var queue =
                store.computeIfAbsent(record.key(), key -> new ConcurrentLinkedDeque<>());
        queue.addFirst(record);
        while (queue.size() > MAX_PER_KEY) {
            queue.pollLast();
        }
    }

    /**
     * greenhouse, sensorType이 null이면 해당 조건은 무시. 최신순.
     */
    public List<TelemetryRecord> findRecent(String greenhouse, String sensorType, int limit) {
        return store.values().stream()
                .flatMap(Deque::stream)
                .filter(r -> greenhouse == null || r.greenhouse().equals(greenhouse))
                .filter(r -> sensorType == null || r.sensorType().equals(sensorType))
                .sorted(Comparator.comparing(TelemetryRecord::receivedAt).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 온실/센서 조합별 가장 최근 값
     */
    public Map<String, TelemetryRecord> lastestByKey() {
        Map<String, TelemetryRecord> result = new TreeMap<>();
        store.forEach((key, queue) -> {
            var first = queue.peekFirst();
            if (first != null) {
                result.put(key, first);
            }
        });
        return result;
    }
}
