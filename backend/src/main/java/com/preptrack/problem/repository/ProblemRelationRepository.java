package com.preptrack.problem.repository;

import com.preptrack.problem.entity.ProblemRelation;
import com.preptrack.problem.entity.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemRelationRepository extends JpaRepository<ProblemRelation, Long> {

    @Query("SELECT pr FROM ProblemRelation pr JOIN FETCH pr.targetProblem WHERE pr.sourceProblem.id = :sourceId")
    List<ProblemRelation> findBySourceProblemIdWithTarget(@Param("sourceId") Long sourceId);

    @Query("SELECT pr FROM ProblemRelation pr JOIN FETCH pr.targetProblem WHERE pr.sourceProblem.id = :sourceId AND pr.relationType = :type")
    List<ProblemRelation> findBySourceProblemIdAndRelationType(@Param("sourceId") Long sourceId, @Param("type") RelationType type);

    boolean existsBySourceProblemIdAndTargetProblemId(Long sourceProblemId, Long targetProblemId);
}
