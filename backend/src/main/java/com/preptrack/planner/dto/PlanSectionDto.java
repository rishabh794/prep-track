package com.preptrack.planner.dto;

import com.preptrack.planner.entity.PlanItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSectionDto {
    private PlanItemType type;
    @Builder.Default
    private List<PlanItemDto> items = new ArrayList<>();
}
