package com.example.smartfarm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttInboundConfig {

    public static final String INBOUND_CHANNEL = "mqttInboundChannel";

    @Bean(name = INBOUND_CHANNEL)
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(
            Mqttv5ClientManager clientManager, MqttProperties props) {
        var topics = props.subscribeTopics().toArray(String[]::new);
        var adapter = new Mqttv5PahoMessageDrivenChannelAdapter(clientManager, topics);

        adapter.setQos(props.defaultQos());
        adapter.setPayloadType(String.class);

        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }
}
