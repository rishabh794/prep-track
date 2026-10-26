package com.preptrack.simulator.repository;

import com.preptrack.simulator.entity.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {
    Optional<SimulationRun> findByIdAndUserId(UUID id, Long userId);
}
