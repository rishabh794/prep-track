package com.preptrack.attempt.repository;

import com.preptrack.attempt.entity.UserProblemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserProblemAttemptRepository extends JpaRepository<UserProblemAttempt, Long> {

    List<UserProblemAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);

    List<UserProblemAttempt> findByUserIdAndProblemIdOrderByAttemptedAtDesc(Long userId, Long problemId);

    List<UserProblemAttempt> findByUserIdAndAttemptedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
