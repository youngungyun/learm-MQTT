package com.example.smartfarm.telemetry;

import java.util.Optional;

public record TelemetryTopic(
        String greenhouse, String sensorType
) {

    public static Optional<TelemetryTopic> parse(String topic) {
        if (topic == null) return Optional.empty();

        var levels = topic.split("/", -1);
        boolean valid = levels.length == 4
                && "farm".equals(levels[0])
                && "telemetry".equals(levels[3])
                && !levels[1].isBlank()
                && !levels[2].isBlank();
        return valid ? Optional.of(new TelemetryTopic(
                levels[1], levels[2]
        )) : Optional.empty();
    }
}
