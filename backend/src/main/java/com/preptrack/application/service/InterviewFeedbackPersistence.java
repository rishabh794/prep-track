package com.preptrack.application.service;

import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewTopic;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.application.repository.InterviewTopicRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/** Short database transactions around an external feedback-extraction call. */
@Service
@RequiredArgsConstructor
public class InterviewFeedbackPersistence {
    private final InterviewRepository interviews;
    private final InterviewTopicRepository topics;
    private final PatternRepository patterns;

    @Transactional(readOnly = true)
    public Long requireOwner(String email, Long interviewId) {
        return interviews.findOwnerIdByInterviewIdAndEmail(interviewId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
    }

    @Transactional
    public void save(Long userId, Long interviewId, String rawFeedback, FeedbackExtractionResult result) {
        // Recheck after extraction: the interview may have been deleted while the AI call ran.
        Interview interview = interviews.findOwnedByIdForUpdate(userId, interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        interview.setRawFeedback(rawFeedback);
        interview.setAiExtracted(result != null && result.isAiUsed());
        interviews.saveAndFlush(interview);

        topics.deleteByInterviewId(interviewId);
        if (result == null || result.getExtractedPatterns() == null) return;
        Set<Long> seen = new HashSet<>();
        result.getExtractedPatterns().stream().filter(extracted -> extracted != null && extracted.getPatternId() != null
                && seen.add(extracted.getPatternId())).forEach(extracted ->
                patterns.findById(extracted.getPatternId()).ifPresent(pattern -> topics.save(InterviewTopic.builder()
                        .interview(interview).pattern(pattern).wasWeak(extracted.isWasWeak()).confirmed(false).build())));
    }
}
