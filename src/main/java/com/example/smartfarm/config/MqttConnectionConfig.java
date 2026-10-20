package com.example.smartfarm.config;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
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
