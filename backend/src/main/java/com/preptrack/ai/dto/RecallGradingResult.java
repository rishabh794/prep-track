package com.preptrack.ai.dto;

import com.preptrack.ai.entity.RecallGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallGradingResult {
    private RecallGrade grade;
    private String reasoning;
}
