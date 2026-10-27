package com.preptrack.company.repository;

import com.preptrack.company.entity.CompanyPatternWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CompanyPatternWeightRepository extends JpaRepository<CompanyPatternWeight, Long> {
    @Query("SELECT w FROM CompanyPatternWeight w JOIN FETCH w.companyProfile JOIN FETCH w.pattern")
    List<CompanyPatternWeight> findAllWithProfileAndPattern();
}
