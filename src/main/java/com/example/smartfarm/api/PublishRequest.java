package com.example.smartfarm.api;

public record PublishRequest(
        String topic,
        String payload,
        Integer qos,
        boolean retained
) {
}
