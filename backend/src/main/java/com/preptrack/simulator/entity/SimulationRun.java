package com.preptrack.simulator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_runs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SimulationRun {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
}
