package com.preptrack.ai.repository;

import com.preptrack.ai.entity.RecallCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecallCheckRepository extends JpaRepository<RecallCheck, Long> {
    List<RecallCheck> findByRevisionCardIdOrderByCreatedAtDesc(Long revisionCardId);
}
