package com.example.smartfarm.mqtt;

import com.example.smartfarm.config.MqttOutboundConfig;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

@MessagingGateway(defaultRequestChannel = MqttOutboundConfig.OUTBOUND_CHANNEL)
public interface MqttGateway {

    /**
     * 기본 QoS, retained=false로 발행
     */
    void publish(@Header(MqttHeaders.TOPIC) String topic, @Payload String payload);

    /**
     * QoS와 retained를 직접 지정해 발행
     */
    void publish(
            @Header(MqttHeaders.TOPIC) String topic,
            @Header(MqttHeaders.QOS) int qos,
            @Header(MqttHeaders.RECEIVED_RETAINED) boolean retained,
            @Payload String payload
    );
}
