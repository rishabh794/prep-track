package com.preptrack.problem.repository;

import com.preptrack.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {
    Optional<Problem> findByCanonicalTitle(String canonicalTitle);

    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.sources WHERE p.id = :id")
    Optional<Problem> findByIdWithSources(@Param("id") Long id);
}
