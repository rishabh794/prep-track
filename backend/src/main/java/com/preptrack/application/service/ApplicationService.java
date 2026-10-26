package com.preptrack.application.service;

import com.preptrack.ai.dto.ConfirmTopicsRequest;
import com.preptrack.ai.dto.ExtractedPatternDto;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.ai.service.FeedbackExtractionService;
import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.dto.InterviewTopicDto;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.entity.InterviewTopic;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.application.repository.InterviewTopicRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.pattern.service.PatternStateService;
import com.preptrack.planner.engine.InterviewRelevanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final InterviewRepository interviewRepository;
    private final InterviewTopicRepository interviewTopicRepository;
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;
    private final FeedbackExtractionService feedbackExtractionService;
    private final PatternStateService patternStateService;
    private final UserPatternStateRepository userPatternStateRepository;
    private final InterviewRelevanceCalculator interviewRelevanceCalculator;

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getUpcomingInterviews(String email) {
        User user = getUserByEmail(email);
        List<Interview> interviews = interviewRepository.findByUserIdAndOutcome(user.getId(), InterviewOutcome.UPCOMING);
        return toDtos(interviews);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getAllInterviews(String email) {
        User user = getUserByEmail(email);
        List<Interview> interviews = interviewRepository.findByUserId(user.getId());
        return toDtos(interviews);
    }

    public FeedbackExtractionResult submitFeedback(String email, Long interviewId, String rawFeedback) {
        User user = getUserByEmail(email);
        getInterviewOwnedByUser(user.getId(), interviewId);

        // OpenAI HTTP call happens outside transaction so DB connections are not starved
        FeedbackExtractionResult result = feedbackExtractionService.extract(rawFeedback);

        saveExtractionResult(user.getId(), interviewId, rawFeedback, result);

        return result;
    }

    @Transactional
    public void saveExtractionResult(Long userId, Long interviewId, String rawFeedback, FeedbackExtractionResult result) {
        Interview interview = getInterviewOwnedByUser(userId, interviewId);
        interview.setRawFeedback(rawFeedback);
        interview.setAiExtracted(result != null && result.isAiUsed());
        interviewRepository.save(interview);

        // Store extracted topics as unconfirmed
        interviewTopicRepository.deleteByInterviewId(interviewId);
        if (result != null && result.getExtractedPatterns() != null) {
            for (ExtractedPatternDto ep : result.getExtractedPatterns()) {
                if (ep.getPatternId() != null) {
                    patternRepository.findById(ep.getPatternId()).ifPresent(p -> {
                        InterviewTopic topic = InterviewTopic.builder()
                                .interview(interview)
                                .pattern(p)
                                .wasWeak(ep.isWasWeak())
                                .confirmed(false)
                                .build();
                        interviewTopicRepository.save(topic);
                    });
                }
            }
        }
    }

    @Transactional
    public InterviewResponseDto confirmTopics(String email, Long interviewId, ConfirmTopicsRequest request) {
        User user = getUserByEmail(email);
        Interview interview = getInterviewOwnedByUser(user.getId(), interviewId);

        boolean wasAlreadyConfirmed = interview.getOutcome() != null && interview.getOutcome() != InterviewOutcome.UPCOMING;

        if (request.getOutcome() != null && !request.getOutcome().isBlank()) {
            try {
                interview.setOutcome(InterviewOutcome.valueOf(request.getOutcome().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid interview outcome value: " + request.getOutcome());
            }
        }
        interview.setAiExtracted(true);
        interviewRepository.save(interview);

        interviewTopicRepository.deleteByInterviewId(interviewId);

        List<InterviewTopicDto> topicDtos = new ArrayList<>();
        if (request.getTopics() != null) {
            for (ConfirmTopicsRequest.ConfirmedTopicDto ct : request.getTopics()) {
                Pattern pattern = patternRepository.findById(ct.getPatternId())
                        .orElseThrow(() -> new ResourceNotFoundException("Pattern not found: " + ct.getPatternId()));

                boolean wasWeak = Boolean.TRUE.equals(ct.getWasWeak());

                InterviewTopic topic = InterviewTopic.builder()
                        .interview(interview)
                        .pattern(pattern)
                        .wasWeak(wasWeak)
                        .confirmed(true)
                        .build();
                InterviewTopic saved = interviewTopicRepository.save(topic);

                topicDtos.add(InterviewTopicDto.builder()
                        .id(saved.getId())
                        .patternId(pattern.getId())
                        .patternName(pattern.getName())
                        .patternSlug(pattern.getSlug())
                        .wasWeak(wasWeak)
                        .confirmed(true)
                        .build());

                // If user struggled on this pattern and interview was not already confirmed:
                // decay existing boost first, then add +0.3 and cap total boost at 0.6
                if (wasWeak && !wasAlreadyConfirmed) {
                    UserPatternState state = patternStateService.getOrCreatePatternState(user, pattern);
                    double decayedBoost = interviewRelevanceCalculator.getEffectiveInterviewBoost(state, LocalDate.now());
                    double newBoost = Math.min(0.6, decayedBoost + 0.3);
                    state.setInterviewBoost(newBoost);
                    state.setInterviewBoostSetAt(LocalDateTime.now());
                    userPatternStateRepository.save(state);
                    log.info("Applied capped interview boost ({}) to pattern {} for user {}", newBoost, pattern.getName(), user.getId());
                }
            }
        }

        InterviewResponseDto dto = toDto(interview);
        dto.setTopics(topicDtos);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<InterviewTopicDto> getInterviewTopics(String email, Long interviewId) {
        User user = getUserByEmail(email);
        getInterviewOwnedByUser(user.getId(), interviewId);

        List<InterviewTopic> topics = interviewTopicRepository.findByInterviewIdWithPattern(interviewId);
        return topics.stream().map(t -> InterviewTopicDto.builder()
                .id(t.getId())
                .patternId(t.getPattern().getId())
                .patternName(t.getPattern().getName())
                .patternSlug(t.getPattern().getSlug())
                .wasWeak(t.getWasWeak())
                .confirmed(t.getConfirmed())
                .build()
        ).collect(Collectors.toList());
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Interview getInterviewOwnedByUser(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        if (interview.getApplication() == null || interview.getApplication().getUser() == null ||
                !interview.getApplication().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Interview does not belong to user: " + interviewId);
        }
        return interview;
    }

    private List<InterviewResponseDto> toDtos(List<Interview> interviews) {
        if (interviews.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> interviewIds = interviews.stream().map(Interview::getId).toList();
        List<InterviewTopic> allTopics = interviewTopicRepository.findByInterviewIdInWithPattern(interviewIds);
        Map<Long, List<InterviewTopic>> topicsByInterviewId = allTopics.stream()
                .collect(Collectors.groupingBy(t -> t.getInterview().getId()));

        return interviews.stream()
                .map(i -> buildDto(i, topicsByInterviewId.getOrDefault(i.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private InterviewResponseDto toDto(Interview i) {
        return buildDto(i, interviewTopicRepository.findByInterviewIdWithPattern(i.getId()));
    }

    private InterviewResponseDto buildDto(Interview i, List<InterviewTopic> topics) {
        List<InterviewTopicDto> topicDtos = topics.stream().map(t -> InterviewTopicDto.builder()
                .id(t.getId())
                .patternId(t.getPattern().getId())
                .patternName(t.getPattern().getName())
                .patternSlug(t.getPattern().getSlug())
                .wasWeak(t.getWasWeak())
                .confirmed(t.getConfirmed())
                .build()
        ).collect(Collectors.toList());

        return InterviewResponseDto.builder()
                .id(i.getId())
                .applicationId(i.getApplication() != null ? i.getApplication().getId() : null)
                .companyName(i.getApplication() != null ? i.getApplication().getCompanyName() : "Interview")
                .role(i.getApplication() != null ? i.getApplication().getRole() : null)
                .interviewDate(i.getInterviewDate())
                .roundName(i.getRoundName())
                .outcome(i.getOutcome() != null ? i.getOutcome().name() : null)
                .notes(i.getNotes() != null ? i.getNotes() : i.getRawFeedback())
                .rawFeedback(i.getRawFeedback())
                .aiExtracted(i.getAiExtracted())
                .topics(topicDtos)
                .build();
    }
}
