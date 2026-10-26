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
        return assign(problem, failureReason, null);
    }

    public FollowUpAssignment assign(Problem problem, FailureReason failureReason, java.util.Map<Long, Problem> prereqMap) {
        if (failureReason == null) {
            return null;
        }

        switch (failureReason) {
            case PATTERN_GAP -> {
                Problem warmup = null;
                if (prereqMap != null && prereqMap.containsKey(problem.getId())) {
                    warmup = prereqMap.get(problem.getId());
                } else {
                    List<ProblemRelation> prereqs = problemRelationRepository
                            .findBySourceProblemIdAndRelationType(problem.getId(), RelationType.EASIER_PREREQUISITE);
                    if (!prereqs.isEmpty()) {
                        warmup = prereqs.get(0).getTargetProblem();
                    }
                }

                String detail = (warmup != null)
                        ? "Try " + warmup.getCanonicalTitle() + " first"
                        : "Review prerequisite concepts in this pattern first";

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

    public String getDetailForType(Problem problem, FollowUpType type) {
        return getDetailForType(problem, type, null);
    }

    public String getDetailForType(Problem problem, FollowUpType type, java.util.Map<Long, Problem> prereqMap) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case EASIER_PREREQUISITE -> {
                Problem warmup = null;
                if (prereqMap != null && prereqMap.containsKey(problem.getId())) {
                    warmup = prereqMap.get(problem.getId());
                } else {
                    List<ProblemRelation> prereqs = problemRelationRepository
                            .findBySourceProblemIdAndRelationType(problem.getId(), RelationType.EASIER_PREREQUISITE);
                    if (!prereqs.isEmpty()) {
                        warmup = prereqs.get(0).getTargetProblem();
                    }
                }
                if (warmup != null) {
                    yield "Try " + warmup.getCanonicalTitle() + " first";
                }
                yield "Review prerequisite concepts in this pattern first";
            }
            case CODE_FROM_SCRATCH -> "Code from scratch, no hints. Timer will run.";
            case EDGE_CASE_LIST -> "List all edge cases before writing code.";
            case TIMED_RETRY -> {
                String target = problem.getOptimalComplexity() != null && !problem.getOptimalComplexity().isBlank()
                        ? problem.getOptimalComplexity()
                        : "optimal complexity";
                yield "Timed retry, target " + target;
            }
        };
    }

    public java.util.Map<Long, Problem> getPrereqMap(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<ProblemRelation> prereqs = problemRelationRepository
                .findBySourceProblemIdInAndRelationType(problemIds, RelationType.EASIER_PREREQUISITE);
        java.util.Map<Long, Problem> map = new java.util.HashMap<>();
        for (ProblemRelation pr : prereqs) {
            map.put(pr.getSourceProblem().getId(), pr.getTargetProblem());
        }
        return map;
    }
}
