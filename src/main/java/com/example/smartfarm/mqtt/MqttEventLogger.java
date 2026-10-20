package com.example.smartfarm.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttIntegrationEvent;
import org.springframework.stereotype.Component;

@Component
public class MqttEventLogger {

    private static final Logger log = LoggerFactory.getLogger(MqttEventLogger.class);

    @EventListener
    public void onMqttEvent(MqttIntegrationEvent event) {
        if (event.getCause() != null) {
            log.warn("MQTT 이벤트 {} : {}", event.getClass().getSimpleName(), event.getCause().toString());
        }
        log.info("MQTT 이벤트 {}", event.getClass().getSimpleName());
    }
}
