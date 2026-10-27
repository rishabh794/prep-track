package com.preptrack.planner.engine;

import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.attempt.entity.*;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.attempt.service.FollowUpAssigner;
import com.preptrack.config.EngineProperties;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.problem.entity.*;
import com.preptrack.problem.repository.ProblemRelationRepository;
import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.repository.RevisionCardRepository;
import com.preptrack.revision.service.RevisionTimeEstimator;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerequisiteCandidateTest {
    @Mock UserProblemStatusRepository statuses;
    @Mock RevisionCardRepository cards;
    @Mock UserSheetRepository sheets;
    @Mock SheetProblemRepository sheetProblems;
    @Mock ProblemPatternRepository patterns;
    @Mock UserPatternStateRepository states;
    @Mock InterviewRepository interviews;
    @Mock AcquisitionScorer acquisition;
    @Mock MaintenanceScorer maintenance;
    @Mock ProblemRelationRepository relations;
    @Mock UserProblemAttemptRepository attempts;
    CandidateCollector collector;
    Problem hard = Problem.builder().id(10L).canonicalTitle("Hard problem").estimatedMinutes(45).build();
    Problem otherHard = Problem.builder().id(11L).canonicalTitle("Other hard problem").estimatedMinutes(45).build();
    Problem warmup = Problem.builder().id(20L).canonicalTitle("Warm-up").estimatedMinutes(15).build();
    LocalDateTime failure = LocalDateTime.of(2026, 10, 27, 12, 0);

    @BeforeEach
    void setup() {
        var policy = new EngineProperties();
        collector = new CandidateCollector(statuses, cards, sheets, sheetProblems, patterns, states, interviews,
                acquisition, maintenance, new RevisionTimeEstimator(policy), new FollowUpAssigner(relations), policy, attempts);
        when(maintenance.score(eq(1L), any(), eq(0.8), any(), anyBoolean(), any(), anyList(), anyMap(), anyList(), any(), anyMap(), anyMap()))
                .thenAnswer(invocation -> {
                    var score = new MaintenanceScorer.ScoredResult(); score.value = 1; score.primaryPatternId = 1L; return score;
                });
    }

    @Test
    void oneSharedWarmupIsScheduledEvenWhenItAlsoHasAFailedAttempt() {
        when(statuses.findByUserIdAndStatus(1L, ProblemStatus.ATTEMPTED)).thenReturn(List.of(
                failed(hard), failed(otherHard), UserProblemStatus.builder().problem(warmup).status(ProblemStatus.ATTEMPTED)
                        .lastOutcome(AttemptOutcome.COULD_NOT_SOLVE).lastFailureReason(FailureReason.IMPLEMENTATION_GAP).lastAttemptedAt(failure).build()));
        when(relations.findBySourceProblemIdInAndRelationType(anyList(), eq(RelationType.EASIER_PREREQUISITE)))
                .thenReturn(List.of(link(hard), link(otherHard)));

        var candidates = collector.collectCandidates(1L, 0.8, failure.toLocalDate().plusDays(1));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getProblem().getId()).isEqualTo(warmup.getId());
        assertThat(candidates.getFirst().getType()).isEqualTo(PlanItemType.RETRY);
        assertThat(candidates.getFirst().getTimeMinutes()).isEqualTo(15);
    }

    @Test
    void anEarlierIndependentWarmupDoesNotSatisfyANewerFailure() {
        when(statuses.findByUserIdAndStatus(1L, ProblemStatus.ATTEMPTED)).thenReturn(List.of(failed(hard)));
        when(statuses.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(completed(failure.minusDays(1))));
        when(attempts.findLatestIndependentCodingByProblemIds(eq(1L), anyList())).thenReturn(List.of(codingPractice(failure.minusDays(1))));
        when(relations.findBySourceProblemIdInAndRelationType(anyList(), eq(RelationType.EASIER_PREREQUISITE))).thenReturn(List.of(link(hard)));

        var candidates = collector.collectCandidates(1L, 0.8, failure.toLocalDate().plusDays(1));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getProblem().getId()).isEqualTo(warmup.getId());
            assertThat(candidate.getFollowUpType()).isEqualTo(FollowUpType.EASIER_PREREQUISITE);
        });
    }

    @Test
    void anIndependentWarmupAtTheSameTimestampAllowsTheOriginalRetry() {
        when(statuses.findByUserIdAndStatus(1L, ProblemStatus.ATTEMPTED)).thenReturn(List.of(failed(hard)));
        when(statuses.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(completed(failure)));
        when(attempts.findLatestIndependentCodingByProblemIds(eq(1L), anyList())).thenReturn(List.of(codingPractice(failure)));
        when(relations.findBySourceProblemIdInAndRelationType(anyList(), eq(RelationType.EASIER_PREREQUISITE))).thenReturn(List.of(link(hard)));

        var candidates = collector.collectCandidates(1L, 0.8, failure.toLocalDate());

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getProblem().getId()).isEqualTo(hard.getId());
            assertThat(candidate.getFollowUpType()).isEqualTo(FollowUpType.CODE_FROM_SCRATCH);
        });
    }

    @Test
    void anAssistedWarmupDoesNotUnlockTheOriginalRetry() {
        when(statuses.findByUserIdAndStatus(1L, ProblemStatus.ATTEMPTED)).thenReturn(List.of(failed(hard)));
        var helped = completed(failure.plusHours(1));
        helped.setLastOutcome(AttemptOutcome.SOLVED_WITH_HELP);
        when(statuses.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(helped));
        when(attempts.findLatestIndependentCodingByProblemIds(eq(1L), anyList())).thenReturn(List.of(codingPractice(failure)));
        when(relations.findBySourceProblemIdInAndRelationType(anyList(), eq(RelationType.EASIER_PREREQUISITE))).thenReturn(List.of(link(hard)));

        assertThat(collector.collectCandidates(1L, 0.8, failure.toLocalDate().plusDays(1))).singleElement()
                .satisfies(candidate -> assertThat(candidate.getProblem().getId()).isEqualTo(warmup.getId()));
    }

    @Test
    void recallOnlyIndependentStatusDoesNotUnlockTheCodingPrerequisite() {
        when(statuses.findByUserIdAndStatus(1L, ProblemStatus.ATTEMPTED)).thenReturn(List.of(failed(hard)));
        when(statuses.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(completed(failure.plusHours(1))));
        when(relations.findBySourceProblemIdInAndRelationType(anyList(), eq(RelationType.EASIER_PREREQUISITE))).thenReturn(List.of(link(hard)));

        assertThat(collector.collectCandidates(1L, 0.8, failure.toLocalDate().plusDays(1))).singleElement()
                .satisfies(candidate -> assertThat(candidate.getProblem().getId()).isEqualTo(warmup.getId()));
    }

    private UserProblemAttemptRepository.IndependentCodingPractice codingPractice(LocalDateTime at) {
        return new UserProblemAttemptRepository.IndependentCodingPractice() {
            public Long getProblemId() { return warmup.getId(); }
            public LocalDateTime getLastCodingAt() { return at; }
        };
    }

    private UserProblemStatus failed(Problem problem) {
        return UserProblemStatus.builder().problem(problem).status(ProblemStatus.ATTEMPTED).lastFailureReason(FailureReason.PATTERN_GAP)
                .lastOutcome(AttemptOutcome.COULD_NOT_SOLVE).lastAttemptedAt(failure).build();
    }

    private UserProblemStatus completed(LocalDateTime at) {
        return UserProblemStatus.builder().problem(warmup).status(ProblemStatus.SOLVED_INDEPENDENTLY)
                .lastOutcome(AttemptOutcome.SOLVED_INDEPENDENTLY).lastAttemptedAt(at).build();
    }

    private ProblemRelation link(Problem source) {
        return ProblemRelation.builder().sourceProblem(source).targetProblem(warmup).relationType(RelationType.EASIER_PREREQUISITE).build();
    }
}
