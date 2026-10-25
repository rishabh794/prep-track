package com.preptrack.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetSelectionResponse {
    private List<String> selectedSheets;
    private int totalProblemsAcrossSheets;
    private int uniqueProblems;
    private int overlapProblems;
}
