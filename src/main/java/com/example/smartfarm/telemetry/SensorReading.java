package com.example.smartfarm.telemetry;

import java.time.Instant;

public record SensorReading(
        String deviceId,
        double value,
        String unit,
        Instant timestamp
) {
}
