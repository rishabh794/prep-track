package com.preptrack.sheet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSheetResponse {
    private Long sheetId;
    private String sheetName;
    private String description;
    private long totalProblems;
    private long solvedProblems;
    private LocalDateTime selectedAt;
}
