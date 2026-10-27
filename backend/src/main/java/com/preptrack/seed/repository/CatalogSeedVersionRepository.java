package com.preptrack.seed.repository;

import com.preptrack.seed.entity.CatalogSeedVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CatalogSeedVersionRepository extends JpaRepository<CatalogSeedVersion, String> {
    Optional<CatalogSeedVersion> findFirstByOrderByAppliedAtDesc();
}
