package com.preptrack.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmTopicsRequest {
    private String outcome;
    private List<ConfirmedTopicDto> topics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmedTopicDto {
        private Long patternId;
        private Boolean wasWeak;
    }
}
