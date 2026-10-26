package com.preptrack.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.simulator.dto.*;
import com.preptrack.simulator.engine.SimulationEngine;
import com.preptrack.simulator.entity.SimulationRun;
import com.preptrack.simulator.repository.SimulationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class SimulatorService {
    private final SimulationCatalogService catalogs;
    private final SimulationEngine engine;
    private final SimulationRunRepository runs;
    private final ObjectMapper mapper;
    private final Semaphore capacity = new Semaphore(1);

    // No transaction is held open while doing CPU-intensive simulation work.
    public SimulationResult run(Long userId, SimulationConfig config) {
        if (!capacity.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A simulation is already running; try again shortly");
        try {
            SimulationResult result = engine.run(config, catalogs.load(config.sheetId()));
            runs.save(SimulationRun.builder().id(result.runId()).userId(userId).createdAt(result.createdAt())
                    .resultJson(mapper.writeValueAsString(result)).build());
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save simulation results", e);
        } finally {
            capacity.release();
        }
    }

    public SimulationResult get(Long userId, UUID runId) {
        var run = runs.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Simulation run not found"));
        try {
            return mapper.readValue(run.getResultJson(), SimulationResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read saved simulation results", e);
        }
    }
}
