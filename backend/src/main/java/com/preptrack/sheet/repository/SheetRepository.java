package com.preptrack.sheet.repository;

import com.preptrack.sheet.entity.Sheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SheetRepository extends JpaRepository<Sheet, Long> {
    Optional<Sheet> findByName(String name);
    List<Sheet> findByIsActiveTrue();
}
