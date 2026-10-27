package com.preptrack.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
    @Pattern(regexp = "(?i)PASSED|FAILED|PARTIAL", message = "Confirmed outcome must be PASSED, FAILED, or PARTIAL")
    private String outcome;
    @NotNull(message = "Topics are required")
    private List<@NotNull @Valid ConfirmedTopicDto> topics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmedTopicDto {
        @NotNull(message = "Pattern ID is required")
        @Positive(message = "Pattern ID must be positive")
        private Long patternId;
        private Boolean wasWeak;
    }
}
