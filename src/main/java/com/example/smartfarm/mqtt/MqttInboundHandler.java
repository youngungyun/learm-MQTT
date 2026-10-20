package com.example.smartfarm.mqtt;

import com.example.smartfarm.config.MqttInboundConfig;
import com.example.smartfarm.telemetry.TelemetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class MqttInboundHandler {

    private final TelemetryService telemetryService;

    @Autowired
    public MqttInboundHandler(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @ServiceActivator(inputChannel = MqttInboundConfig.INBOUND_CHANNEL)
    public void handle(
            @Payload String payload,
            @Header(MqttHeaders.RECEIVED_TOPIC) String topic,
            @Header(MqttHeaders.RECEIVED_QOS) int qos
    ) {
        telemetryService.handle(topic, payload, qos);
    }
}
