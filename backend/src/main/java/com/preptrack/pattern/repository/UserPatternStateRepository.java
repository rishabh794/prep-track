package com.preptrack.pattern.repository;

import com.preptrack.pattern.entity.UserPatternState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPatternStateRepository extends JpaRepository<UserPatternState, Long> {

    Optional<UserPatternState> findByUserIdAndPatternId(Long userId, Long patternId);

    @Query("SELECT ups FROM UserPatternState ups JOIN FETCH ups.pattern WHERE ups.user.id = :userId")
    List<UserPatternState> findByUserIdWithPattern(@Param("userId") Long userId);

    List<UserPatternState> findByUserId(Long userId);

    List<UserPatternState> findByUserIdAndPatternIdIn(Long userId, Collection<Long> patternIds);
}
