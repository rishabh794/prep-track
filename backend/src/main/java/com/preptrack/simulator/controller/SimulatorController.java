package com.preptrack.simulator.controller;

import com.preptrack.auth.security.UserDetailsImpl;
import com.preptrack.simulator.dto.*;
import com.preptrack.simulator.service.SimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {
    private final SimulatorService simulator;

    @PostMapping("/run")
    public ResponseEntity<SimulationResult> run(@AuthenticationPrincipal UserDetailsImpl user,
                                               @Valid @RequestBody SimulationConfig config) {
        var result = simulator.run(user.getId(), config);
        return ResponseEntity.created(URI.create("/api/simulator/results/" + result.runId())).body(result);
    }

    @GetMapping("/results/{runId}")
    public SimulationResult get(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID runId) {
        return simulator.get(user.getId(), runId);
    }

    @GetMapping(value = "/results/{runId}/summary", produces = MediaType.TEXT_PLAIN_VALUE)
    public String summary(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID runId) {
        return simulator.get(user.getId(), runId).textSummary();
    }
}
