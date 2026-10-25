package com.preptrack.pattern.repository;

import com.preptrack.pattern.entity.ProblemPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemPatternRepository extends JpaRepository<ProblemPattern, Long> {
    List<ProblemPattern> findByProblemId(Long problemId);
    List<ProblemPattern> findByPatternId(Long patternId);

    @Query("SELECT pp FROM ProblemPattern pp JOIN FETCH pp.pattern WHERE pp.problem.id = :problemId")
    List<ProblemPattern> findByProblemIdWithPattern(@Param("problemId") Long problemId);

    @Query("SELECT pp FROM ProblemPattern pp JOIN FETCH pp.pattern WHERE pp.problem.id IN :problemIds")
    List<ProblemPattern> findByProblemIdInWithPattern(@Param("problemIds") List<Long> problemIds);
}
