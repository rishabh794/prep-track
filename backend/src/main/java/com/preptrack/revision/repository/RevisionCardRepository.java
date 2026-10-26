package com.preptrack.revision.repository;

import com.preptrack.revision.entity.RevisionCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RevisionCardRepository extends JpaRepository<RevisionCard, Long> {

    Optional<RevisionCard> findByUserIdAndProblemId(Long userId, Long problemId);

    @Query("SELECT rc FROM RevisionCard rc JOIN FETCH rc.problem WHERE rc.user.id = :userId AND rc.nextReviewDate <= :date ORDER BY rc.nextReviewDate ASC")
    List<RevisionCard> findDueCardsWithProblem(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(rc) FROM RevisionCard rc WHERE rc.user.id = :userId AND rc.nextReviewDate < :date")
    int countOverdueCards(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(rc) FROM RevisionCard rc WHERE rc.user.id = :userId AND rc.nextReviewDate <= :date")
    int countDueCards(@Param("userId") Long userId, @Param("date") LocalDate date);

    List<RevisionCard> findByUserId(Long userId);
}
