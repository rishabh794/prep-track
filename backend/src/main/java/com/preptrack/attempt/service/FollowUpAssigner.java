package com.preptrack.attempt.service;

import com.preptrack.attempt.entity.FailureReason;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.entity.ProblemRelation;
import com.preptrack.problem.entity.RelationType;
import com.preptrack.problem.repository.ProblemRelationRepository;
import com.preptrack.revision.entity.FollowUpType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpAssigner {

    private final ProblemRelationRepository problemRelationRepository;

    public FollowUpAssignment assign(Problem problem, FailureReason failureReason) {
        if (failureReason == null) {
            return null;
        }

        switch (failureReason) {
            case PATTERN_GAP -> {
                List<ProblemRelation> prereqs = problemRelationRepository
                        .findBySourceProblemIdAndRelationType(problem.getId(), RelationType.EASIER_PREREQUISITE);

                String detail;
                if (!prereqs.isEmpty()) {
                    Problem warmup = prereqs.get(0).getTargetProblem();
                    detail = "Try " + warmup.getCanonicalTitle() + " first";
                } else {
                    detail = "Review prerequisite concepts in this pattern first";
                }

                return FollowUpAssignment.builder()
                        .followUpType(FollowUpType.EASIER_PREREQUISITE)
                        .followUpDetail(detail)
                        .build();
            }
            case IMPLEMENTATION_GAP -> {
                return FollowUpAssignment.builder()
                        .followUpType(FollowUpType.CODE_FROM_SCRATCH)
                        .followUpDetail("Code from scratch, no hints. Timer will run.")
                        .build();
            }
            case EDGE_CASE_MISS -> {
                return FollowUpAssignment.builder()
                        .followUpType(FollowUpType.EDGE_CASE_LIST)
                        .followUpDetail("List all edge cases before writing code.")
                        .build();
            }
            case TOO_SLOW -> {
                String target = problem.getOptimalComplexity() != null && !problem.getOptimalComplexity().isBlank()
                        ? problem.getOptimalComplexity()
                        : "optimal complexity";
                return FollowUpAssignment.builder()
                        .followUpType(FollowUpType.TIMED_RETRY)
                        .followUpDetail("Timed retry, target " + target)
                        .build();
            }
            default -> {
                return null;
            }
        }
    }
}
