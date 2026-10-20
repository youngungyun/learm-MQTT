package com.example.smartfarm.telemetry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryRepository repository;

    @Autowired
    public TelemetryController(TelemetryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TelemetryRecord> list(
            @RequestParam(required = false) String greenhouse,
            @RequestParam(required = false) String sensorType,
            @RequestParam(defaultValue = "50") int limit

    ) {
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit는 1~500이어야 합니다.");
        }
        return repository.findRecent(greenhouse, sensorType, limit);
    }

    @GetMapping("/latest")
    public Map<String, TelemetryRecord> lastest() {
        return repository.lastestByKey();
    }
}
