package com.example.smartfarm.telemetry;

import java.time.Instant;

public record TelemetryRecord(
        String greenhouse,
        String sensorType,
        SensorReading reading,
        Instant receivedAt,
        int qos
) {

    public String key() {
        return greenhouse + "/" + sensorType;
    }
}
