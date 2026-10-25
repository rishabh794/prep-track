package com.preptrack.application.repository;

import com.preptrack.application.entity.InterviewTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewTopicRepository extends JpaRepository<InterviewTopic, Long> {

    @Query("SELECT it FROM InterviewTopic it JOIN FETCH it.pattern WHERE it.interview.id = :interviewId")
    List<InterviewTopic> findByInterviewIdWithPattern(@Param("interviewId") Long interviewId);
}
