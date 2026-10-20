package com.example.smartfarm.config;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Configuration
public class MqttConnectionConfig {

    @Bean
    public MqttConnectionOptions mqttConnectionOptions(
            MqttProperties props) {
        var options = new MqttConnectionOptions();
        options.setServerURIs(new String[] {props.brokerUrl()});
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(props.keepAliveSeconds());
        options.setCleanStart(props.cleanStart());
        options.setSessionExpiryInterval(props.sessionExpirySeconds());

        // LWT: 비정상 종료 시 브로커가 대신 발행할 메시지
        var will = new MqttMessage("offline".getBytes(StandardCharsets.UTF_8));
        will.setQos(1);
        will.setRetained(true);
        options.setWill(props.statusTopic(), will);

        if (StringUtils.hasText(props.username())) {
            options.setUserName(props.username());
            options.setPassword(props.password().getBytes(StandardCharsets.UTF_8));
        }
        return options;
    }

    @Bean
    public Mqttv5ClientManager mqttClientManager(MqttConnectionOptions options, MqttProperties props) {
        var clientManager = new Mqttv5ClientManager(options, props.clientId());
        clientManager.setPersistence(new MemoryPersistence());
        return clientManager;
    }
}
