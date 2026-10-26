package com.preptrack.sheet.repository;

import com.preptrack.sheet.entity.SheetProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface SheetProblemRepository extends JpaRepository<SheetProblem, Long> {

    @Query("SELECT sp FROM SheetProblem sp JOIN FETCH sp.problem WHERE sp.sheet.id = :sheetId ORDER BY sp.displayOrder ASC")
    List<SheetProblem> findBySheetIdOrderByDisplayOrderAsc(@Param("sheetId") Long sheetId);

    @Query("SELECT sp FROM SheetProblem sp JOIN FETCH sp.problem WHERE sp.sheet.id IN :sheetIds ORDER BY sp.displayOrder ASC")
    List<SheetProblem> findBySheetIdInOrderByDisplayOrderAsc(@Param("sheetIds") List<Long> sheetIds);

    @Query("SELECT sp.problem.id FROM SheetProblem sp WHERE sp.sheet.name = :sheetName")
    Set<Long> findProblemIdsBySheetName(@Param("sheetName") String sheetName);

    long countBySheetId(Long sheetId);

    @Query("SELECT sp.sheet.id, COUNT(sp) FROM SheetProblem sp GROUP BY sp.sheet.id")
    List<Object[]> countProblemsGroupedBySheet();

    boolean existsBySheetIdAndProblemId(Long sheetId, Long problemId);

    @Query("SELECT sp.sheet.id, sp.problem.id FROM SheetProblem sp WHERE sp.sheet.id IN :sheetIds")
    List<Object[]> findSheetAndProblemIdsBySheetIdIn(@Param("sheetIds") java.util.Collection<Long> sheetIds);

    @Query("SELECT DISTINCT sp.problem.id FROM SheetProblem sp WHERE sp.sheet.id IN :sheetIds")
    Set<Long> findDistinctProblemIdsBySheetIdIn(@Param("sheetIds") java.util.Collection<Long> sheetIds);

    @Query("SELECT sp.sheet.name FROM SheetProblem sp WHERE sp.problem.id = :problemId")
    List<String> findSheetNamesByProblemId(@Param("problemId") Long problemId);
}
