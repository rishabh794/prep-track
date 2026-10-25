package com.preptrack.problem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSourceDto {
    private String platform;
    private String externalId;
    private String externalUrl;
    private String externalTitle;
}
