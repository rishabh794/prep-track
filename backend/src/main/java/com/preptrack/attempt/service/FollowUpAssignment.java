package com.preptrack.attempt.service;

import com.preptrack.revision.entity.FollowUpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpAssignment {
    private FollowUpType followUpType;
    private String followUpDetail;
}
