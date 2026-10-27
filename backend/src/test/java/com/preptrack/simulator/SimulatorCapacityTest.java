package com.preptrack.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.simulator.dto.*;
import com.preptrack.simulator.engine.*;
import com.preptrack.simulator.repository.SimulationRunRepository;
import com.preptrack.simulator.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulatorCapacityTest {
    @Test
    void rejectsConcurrentRunsAndReleasesCapacityAfterSuccessAndFailure() throws Exception {
        var catalogs = mock(SimulationCatalogService.class);
        var engine = mock(SimulationEngine.class);
        var mapper = mock(ObjectMapper.class);
        var service = new SimulatorService(catalogs, engine, mock(SimulationRunRepository.class), mapper);
        var config = new SimulationConfig(1, 1, 30, 1, 1, 0.8, 42L, null);
        var catalog = new SimulationCatalog("Test", List.of(), List.of(), Map.of());
        var result = new SimulationResult(UUID.randomUUID(), Instant.now(), "test", config, "Test", 0, 0,
                List.of(), List.of(), List.of(), null, "test");
        when(catalogs.load(null)).thenReturn(catalog);
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(engine.run(config, catalog)).thenAnswer(invocation -> {
            started.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker timed out");
            return result;
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<SimulationResult> first = executor.submit(() -> service.run(1L, config));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.run(2L, config)).isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(429));
            } finally { finish.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(result);
        }
        when(catalogs.load(null)).thenThrow(new BadRequestException("Invalid catalog"));
        assertThatThrownBy(() -> service.run(1L, config)).isInstanceOf(BadRequestException.class);
        doReturn(catalog).when(catalogs).load(null);
        assertThat(service.run(1L, config)).isEqualTo(result);
    }
}
