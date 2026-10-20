package com.example.smartfarm.mqtt;

import com.example.smartfarm.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;

@Component
public class ServerStatusPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusPublisher.class);

    private final MqttGateway gateway;
    private final MqttProperties props;

    @Autowired
    public ServerStatusPublisher(MqttGateway gateway, MqttProperties props) {
        this.gateway = gateway;
        this.props = props;
    }

    @EventListener
    public void onSubscribed(MqttSubscribedEvent event) {
        log.info("구독 완료 - online 발행 ({})", event.getMessage());
        gateway.publish(props.statusTopic(), 1, true, "online");
    }

    @EventListener
    public void onShutdown(ContextClosedEvent event) {
        log.info("정상 종료 - offline 발행");
        gateway.publish(props.statusTopic(), 1, true, "offline");
    }
}
