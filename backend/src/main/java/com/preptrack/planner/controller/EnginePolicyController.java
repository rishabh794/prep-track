package com.preptrack.planner.controller;

import com.preptrack.config.EngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnginePolicyController {
    private final EngineProperties policy;
    @GetMapping("/api/planner/policy")
    public EngineProperties getPolicy() { return policy.snapshot(); }
}
