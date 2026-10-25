package com.preptrack.application.repository;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE a.user.id = :userId ORDER BY i.interviewDate ASC")
    List<Interview> findByUserId(@Param("userId") Long userId);

    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE a.user.id = :userId AND i.outcome = :outcome ORDER BY i.interviewDate ASC")
    List<Interview> findByUserIdAndOutcome(@Param("userId") Long userId, @Param("outcome") InterviewOutcome outcome);

    List<Interview> findByApplicationIdOrderByInterviewDateAsc(Long applicationId);
}
