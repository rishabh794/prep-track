package com.preptrack.problem.repository;

import com.preptrack.problem.entity.Platform;
import com.preptrack.problem.entity.ProblemSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemSourceRepository extends JpaRepository<ProblemSource, Long> {
    Optional<ProblemSource> findByPlatformAndExternalId(Platform platform, String externalId);
    List<ProblemSource> findByProblemId(Long problemId);

    @org.springframework.data.jpa.repository.Query("SELECT ps FROM ProblemSource ps JOIN FETCH ps.problem")
    List<ProblemSource> findAllWithProblem();
}
