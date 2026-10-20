package com.example.smartfarm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String username,
        String password,
        int defaultQos,
        List<String> subscribeTopics,
        boolean cleanStart,
        long sessionExpirySeconds,
        int keepAliveSeconds,
        String statusTopic
) {
}
