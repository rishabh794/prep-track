package com.preptrack.sheet.repository;

import com.preptrack.sheet.entity.UserSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSheetRepository extends JpaRepository<UserSheet, Long> {

    @Query("SELECT us FROM UserSheet us JOIN FETCH us.sheet WHERE us.user.id = :userId")
    List<UserSheet> findByUserIdWithSheet(@Param("userId") Long userId);

    Optional<UserSheet> findByUserIdAndSheetId(Long userId, Long sheetId);

    boolean existsByUserIdAndSheetId(Long userId, Long sheetId);

    void deleteByUserIdAndSheetId(Long userId, Long sheetId);
}
