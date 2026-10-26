package com.preptrack.attempt.repository;

import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProblemStatusRepository extends JpaRepository<UserProblemStatus, Long> {

    Optional<UserProblemStatus> findByUserIdAndProblemId(Long userId, Long problemId);

    List<UserProblemStatus> findByUserId(Long userId);

    List<UserProblemStatus> findByUserIdAndStatus(Long userId, ProblemStatus status);

    List<UserProblemStatus> findByUserIdAndProblemIdIn(Long userId, Collection<Long> problemIds);

    @Query("SELECT ups FROM UserProblemStatus ups WHERE ups.user.id = :userId AND ups.status IN :statuses")
    List<UserProblemStatus> findByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") Collection<ProblemStatus> statuses);

    @Query("SELECT COUNT(ups) FROM UserProblemStatus ups WHERE ups.user.id = :userId AND ups.status IN ('SOLVED_WITH_HELP', 'SOLVED_INDEPENDENTLY')")
    long countSolvedByUserId(@Param("userId") Long userId);
}
