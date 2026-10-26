package com.preptrack.attempt.dto;

import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.pattern.dto.PatternUpdateDto;
import com.preptrack.revision.dto.RevisionScheduledDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponseDto {

    private Long attemptId;
    private StatusUpdateDto statusUpdate;
    private List<PatternUpdateDto> patternUpdates;
    private RevisionScheduledDto revisionScheduled;
    private Boolean planItemCompleted;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateDto {
        private Long problemId;
        private ProblemStatus newStatus;
        private List<String> sheetsAffected;
    }
}
