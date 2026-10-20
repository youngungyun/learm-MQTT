package com.example.smartfarm.api;

import com.example.smartfarm.mqtt.MqttGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/greenhouses")
public class CommandController {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    @Autowired
    public CommandController(MqttGateway gateway, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{greenhouse}/commands")
    public ResponseEntity<Map<String, Object>> send(
            @PathVariable String greenhouse,
            @RequestBody CommandRequest request) {
        if (!NAME.matcher(greenhouse).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "온실 이름은 영문, 숫자, _, - 만 가능합니다.");
        }
        if (request.device() == null || request.action() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "device와 action은 필수입니다.");
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("device", request.device());
        body.put("action", request.action());
        body.put("params", request.params() == null ? Map.of() : request.params());
        body.put("issuedAt", Instant.now().toString());

        var topic = "farm/%s/commands".formatted(greenhouse);
        gateway.publish(topic, 1, false, jsonMapper.writeValueAsString(body));

        return ResponseEntity.accepted().body(Map.of(
                "topic", topic,
                "command", body
        ));
    }
}
