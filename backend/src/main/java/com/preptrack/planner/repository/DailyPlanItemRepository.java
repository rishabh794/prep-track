package com.preptrack.planner.repository;

import com.preptrack.planner.entity.DailyPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, Long> {

    List<DailyPlanItem> findByDailyPlanIdOrderByDisplayOrderAsc(Long planId);

    @Query("SELECT dpi FROM DailyPlanItem dpi JOIN dpi.dailyPlan dp WHERE dp.user.id = :userId AND dp.planDate = :planDate AND dpi.problem.id = :problemId")
    Optional<DailyPlanItem> findItemForToday(
            @Param("userId") Long userId,
            @Param("planDate") LocalDate planDate,
            @Param("problemId") Long problemId
    );

    @Query("SELECT dpi FROM DailyPlanItem dpi JOIN dpi.dailyPlan dp WHERE dpi.id = :itemId AND dp.user.id = :userId")
    Optional<DailyPlanItem> findByIdAndUserId(@Param("itemId") Long itemId, @Param("userId") Long userId);
}
