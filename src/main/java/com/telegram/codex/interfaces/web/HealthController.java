package com.telegram.codex.interfaces.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiStatusResponse show() {
        return new ApiStatusResponse(true);
    }
}
