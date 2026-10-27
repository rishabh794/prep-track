package com.preptrack.attempt.repository;

import com.preptrack.attempt.entity.UserProblemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

@Repository
public interface UserProblemAttemptRepository extends JpaRepository<UserProblemAttempt, Long> {

    interface IndependentCodingPractice {
        Long getProblemId();
        LocalDateTime getLastCodingAt();
    }

    // Match history ordering, including equal-timestamp events. A later failed or
    // assisted coding attempt must not be hidden by an older independent solve.
    @Query("SELECT a.problem.id AS problemId, a.attemptedAt AS lastCodingAt "
            + "FROM UserProblemAttempt a WHERE a.user.id = :userId AND a.problem.id IN :problemIds "
            + "AND a.outcome = com.preptrack.attempt.entity.AttemptOutcome.SOLVED_INDEPENDENTLY "
            + "AND a.activityType IN (com.preptrack.attempt.entity.AttemptActivityType.CODING_ATTEMPT, "
            + "com.preptrack.attempt.entity.AttemptActivityType.CODING_REVIEW) "
            + "AND NOT EXISTS (SELECT newer.id FROM UserProblemAttempt newer "
            + "WHERE newer.user.id = :userId AND newer.problem.id = a.problem.id "
            + "AND newer.activityType IN (com.preptrack.attempt.entity.AttemptActivityType.CODING_ATTEMPT, "
            + "com.preptrack.attempt.entity.AttemptActivityType.CODING_REVIEW) "
            + "AND (newer.attemptedAt > a.attemptedAt OR (newer.attemptedAt = a.attemptedAt AND newer.id > a.id)))")
    List<IndependentCodingPractice> findLatestIndependentCodingByProblemIds(
            @Param("userId") Long userId, @Param("problemIds") Collection<Long> problemIds);

    @EntityGraph(attributePaths = "problem")
    Page<UserProblemAttempt> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "problem")
    Page<UserProblemAttempt> findByUserIdAndProblemId(Long userId, Long problemId, Pageable pageable);

    List<UserProblemAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);

    List<UserProblemAttempt> findByUserIdAndProblemIdOrderByAttemptedAtDesc(Long userId, Long problemId);

    List<UserProblemAttempt> findByUserIdAndAttemptedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
