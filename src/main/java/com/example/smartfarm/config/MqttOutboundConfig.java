package com.example.smartfarm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttOutboundConfig {

    public static final String OUTBOUND_CHANNEL = "mqttOutboundChannel";

    @Bean(name = OUTBOUND_CHANNEL)
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow(Mqttv5ClientManager clientManager, MqttProperties props) {
        var handler = new Mqttv5PahoMessageHandler(clientManager);

        // 브로커 응답을 기다리지 않고 바로 반환.
        handler.setAsync(true);
        handler.setDefaultQos(props.defaultQos());
        handler.setDefaultRetained(true);

        return IntegrationFlow.from(OUTBOUND_CHANNEL)
                .handle(handler)
                .get();
    }
}
