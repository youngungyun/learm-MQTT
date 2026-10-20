package com.example.smartfarm.alert;

import java.time.Instant;

public record Alert(
        String greenhouse,
        String sensorType,
        String deviceId,
        double value,
        double threshold,
        String message,
        Instant occurredAt
) {
}
