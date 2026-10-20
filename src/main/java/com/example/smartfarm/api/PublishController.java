package com.example.smartfarm.api;

import com.example.smartfarm.mqtt.MqttGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/mqtt")
public class PublishController {

    private final MqttGateway mqttGateway;

    @Autowired
    public PublishController(MqttGateway mqttGateway) {
        this.mqttGateway = mqttGateway;
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestBody PublishRequest request) {
        validateTopic(request.topic());
        int qos = request.qos() == null ? 1 : request.qos();
        if (qos < 0 || qos > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qos는 0, 1, 2 중 하나여야 합니다.");
        }
        var payload = request.payload() == null ? "" : request.payload();

        mqttGateway.publish(request.topic(), qos, request.retained(), payload);

        return ResponseEntity.accepted().body(Map.of(
                "topic", request.topic(),
                "qos", qos,
                "retained", request.retained()
        ));
    }

    private void validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic은 필수입니다.");
        }
        if (topic.contains("+") || topic.contains("#")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발행 토픽에는 와일드카드(+, #)를 쓸 수 없습니다.");
        }
    }
}
