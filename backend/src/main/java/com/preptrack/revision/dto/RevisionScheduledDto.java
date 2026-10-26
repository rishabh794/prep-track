package com.preptrack.revision.dto;

import com.preptrack.revision.entity.FollowUpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionScheduledDto {
    private LocalDate nextReviewDate;
    private FollowUpType followUpType;
}
