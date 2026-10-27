package com.preptrack.application.repository;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    @Query("SELECT a.user.id FROM Interview i JOIN i.application a WHERE i.id = :interviewId AND a.user.email = :email")
    Optional<Long> findOwnerIdByInterviewIdAndEmail(@Param("interviewId") Long interviewId, @Param("email") String email);

    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE i.id = :interviewId AND a.user.id = :userId")
    Optional<Interview> findOwnedById(@Param("userId") Long userId, @Param("interviewId") Long interviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE i.id = :interviewId AND a.user.id = :userId")
    Optional<Interview> findOwnedByIdForUpdate(@Param("userId") Long userId, @Param("interviewId") Long interviewId);

    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE a.user.id = :userId ORDER BY i.interviewDate ASC")
    List<Interview> findByUserId(@Param("userId") Long userId);

    @Query("SELECT i FROM Interview i JOIN FETCH i.application a WHERE a.user.id = :userId AND i.outcome = :outcome ORDER BY i.interviewDate ASC")
    List<Interview> findByUserIdAndOutcome(@Param("userId") Long userId, @Param("outcome") InterviewOutcome outcome);

    List<Interview> findByApplicationIdOrderByInterviewDateAsc(Long applicationId);
}
