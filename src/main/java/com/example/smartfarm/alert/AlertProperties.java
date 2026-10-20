package com.example.smartfarm.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.alert")
public record AlertProperties(
        double temperatureMax, double humidityMin
) {
}
