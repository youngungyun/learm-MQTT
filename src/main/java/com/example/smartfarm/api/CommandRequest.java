package com.example.smartfarm.api;

import java.util.Map;

public record CommandRequest(
        String device,
        String action,
        Map<String, Object> params
) {
}
