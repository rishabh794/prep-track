package com.preptrack.pattern.repository;

import com.preptrack.pattern.entity.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatternRepository extends JpaRepository<Pattern, Long> {
    Optional<Pattern> findBySlug(String slug);
    Optional<Pattern> findByName(String name);
}
