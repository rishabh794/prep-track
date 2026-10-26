package com.preptrack.planner.repository;

import com.preptrack.planner.entity.DailyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyPlanRepository extends JpaRepository<DailyPlan, Long> {

    Optional<DailyPlan> findByUserIdAndPlanDate(Long userId, LocalDate planDate);

    @Query("SELECT DISTINCT dp FROM DailyPlan dp LEFT JOIN FETCH dp.items i LEFT JOIN FETCH i.problem WHERE dp.user.id = :userId AND dp.planDate = :planDate")
    Optional<DailyPlan> findByUserIdAndPlanDateWithItems(@Param("userId") Long userId, @Param("planDate") LocalDate planDate);

    void deleteByUserIdAndPlanDate(Long userId, LocalDate planDate);
}
