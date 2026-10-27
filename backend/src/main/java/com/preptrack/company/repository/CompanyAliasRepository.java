package com.preptrack.company.repository;

import com.preptrack.company.entity.CompanyAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {
    @Query("SELECT a FROM CompanyAlias a JOIN FETCH a.companyProfile")
    List<CompanyAlias> findAllWithProfile();
}
