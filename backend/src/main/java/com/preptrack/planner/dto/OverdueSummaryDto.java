package com.preptrack.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverdueSummaryDto {
    private int totalOverdue;
    private int showingToday;
    private String message;
}
