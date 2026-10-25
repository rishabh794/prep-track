package com.preptrack.sheet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSheetResponse {
    private Long id;
    private String name;
    private String description;
    private String sourceUrl;
    private long totalProblems;
}
