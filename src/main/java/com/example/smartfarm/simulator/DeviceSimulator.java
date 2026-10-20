package com.example.smartfarm.simulator;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(prefix = "smartfarm.simulator", name = "enabled", havingValue = "true")
public class DeviceSimulator {

    private static final Logger log = LoggerFactory.getLogger(DeviceSimulator.class);
    private static final List<String> GREENHOUSE = List.of("gh1", "gh2");

    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    @Autowired
    public DeviceSimulator(MqttGateway gateway, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelayString = "${smartfarm.simulator.interval-ms:5000}")
    public void publishReadings() {
        for (String gh : GREENHOUSE) {
            publish(gh, "temperature", "C", randomTemperature());
            publish(gh, "humidity", "%", randomHumidity());
        }
    }

    private void publish(String greenhouse, String sensorType, String unit, double value) {
        var rounded = Math.round(value * 10) / 10.0;
        var deviceId = "%s-%s-01".formatted(greenhouse, sensorType.substring(0, 4));
        var reading = new SensorReading(deviceId, rounded, unit, Instant.now());
        var topic = "farm/%s/%s/telemetry".formatted(greenhouse, sensorType);

        gateway.publish(topic, 0, false, jsonMapper.writeValueAsString(reading));
        log.debug("시뮬레이터 발행 {} = {}{}", topic, rounded, unit);
    }


    // 평소 20~28, 10% 확률로 31~36 (임계값 30% 초과)
    private double randomTemperature() {
        var r = ThreadLocalRandom.current();
        return r.nextInt(10) == 0 ? r.nextDouble(31, 36) : r.nextDouble(20, 28);
    }

    // 평소 50 ~ 70, 10% 확률로 30~39% (임계값 40% 미만)
    private double randomHumidity() {
        var r = ThreadLocalRandom.current();
        return r.nextInt(10) == 0 ? r.nextDouble(30, 39) : r.nextDouble(50, 70);
    }
}