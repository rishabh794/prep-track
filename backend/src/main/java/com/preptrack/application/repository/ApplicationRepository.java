package com.preptrack.application.repository;

import com.preptrack.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByUserIdOrderByCreatedAtDesc(Long userId);
    java.util.Optional<Application> findByIdAndUserId(Long id, Long userId);
}
