package com.example.smartfarm.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final JsonMapper jsonMapper;
    private final TelemetryRepository repository;

    @Autowired
    public TelemetryService(JsonMapper jsonMapper, TelemetryRepository repository) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    public void handle(String topic, String payload, int qos) {
        var parsedTopic = TelemetryTopic.parse(topic);
        if (parsedTopic.isEmpty()) {
            log.warn("텔레메트리 토픽 형식이 아님: {}", topic);
            return;
        }

        SensorReading reading;
        try {
            reading = jsonMapper.readValue(payload,
                    SensorReading.class);
        } catch (JacksonException e) {
            log.warn("JSON 파싱 실패 topic={} payload={} : {}",
                    topic, payload, e.getMessage());
            return;
        }
        if (reading.deviceId() == null ||
        reading.deviceId().isBlank()) {
            log.warn("deviceId 누락 topic={} payload={}",
                    topic, payload);
            return;
        }

        var record = new TelemetryRecord(
                parsedTopic.get().greenhouse(),
                parsedTopic.get().sensorType(),
                reading,
                Instant.now(),
                qos
        );
        repository.save(record);
        log.debug("저장 {} = {}{}", record.key(), reading.value(), reading.unit());
    }
}
