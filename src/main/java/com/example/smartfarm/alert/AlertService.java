package com.example.smartfarm.alert;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.TelemetryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertProperties props;
    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    @Autowired
    public AlertService(AlertProperties props, MqttGateway gateway, JsonMapper jsonMapper) {
        this.props = props;
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    public void evaluate(TelemetryRecord record) {
        double value = record.reading().value();
        switch (record.sensorType()) {
            case "temperature" -> {
                if (value > props.temperatureMax()) {
                    raise(record, props.temperatureMax(),
                            "온도 %.1f도가 상한 %.1f도를 초과했습니다.".formatted(
                                    value, props.temperatureMax()
                            ));
                }
            }
            case "humidity" -> {
                if (value < props.humidityMin()) {
                    raise(record, props.humidityMin(),
                            "습도 %.1f%%가 하한 %.1f%% 미만입니다.".formatted(
                                    value, props.humidityMin()
                            ));
                }
            }
            default -> {}
        }
    }

    private void raise(TelemetryRecord record, double threshold, String message) {
        var alert = new Alert(
                record.greenhouse(),
                record.sensorType(),
                record.reading().deviceId(),
                record.reading().value(),
                threshold,
                message,
                Instant.now()
        );
        var topic = "farm/%s/alerts".formatted(record.greenhouse());
        gateway.publish(topic, 1, false, jsonMapper.writeValueAsString(alert));
        log.warn("경보 발행 {} : {}", topic, message);
    }
}
