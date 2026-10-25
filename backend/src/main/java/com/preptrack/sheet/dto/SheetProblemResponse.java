package com.preptrack.sheet.dto;

import com.preptrack.pattern.dto.PatternSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetProblemResponse {
    private Long id;
    private String canonicalTitle;
    private String difficulty;
    private Integer displayOrder;
    private String category;
    private String status;
    private List<PatternSummaryDto> patterns;
}
