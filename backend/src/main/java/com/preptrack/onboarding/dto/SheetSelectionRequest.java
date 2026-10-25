package com.preptrack.onboarding.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetSelectionRequest {
    @NotEmpty(message = "At least one sheet must be selected")
    private List<Long> sheetIds;
}
