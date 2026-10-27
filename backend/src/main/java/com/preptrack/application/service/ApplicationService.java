package com.preptrack.application.service;

import com.preptrack.config.EngineProperties;

import com.preptrack.ai.dto.ConfirmTopicsRequest;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.ai.service.FeedbackExtractionService;
import com.preptrack.application.dto.ApplicationRequestDto;
import com.preptrack.application.dto.ApplicationResponseDto;
import com.preptrack.application.dto.InterviewRequestDto;
import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.dto.InterviewTopicDto;
import com.preptrack.application.entity.Application;
import com.preptrack.application.entity.ApplicationStatus;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.entity.InterviewTopic;
import com.preptrack.application.repository.ApplicationRepository;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.application.repository.InterviewTopicRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.common.service.TimeService;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final EngineProperties policy;

    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewTopicRepository interviewTopicRepository;
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;
    private final FeedbackExtractionService feedbackExtractionService;
    private final PatternStateService patternStateService;
    private final UserPatternStateRepository userPatternStateRepository;
    private final InterviewRelevanceCalculator interviewRelevanceCalculator;
    private final InterviewFeedbackPersistence feedbackPersistence;
    private final TimeService timeService;

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getUpcomingInterviews(String email) {
        User user = getUserByEmail(email);
        return getUpcomingInterviews(user.getId());
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getUpcomingInterviews(Long userId) {
        List<Interview> interviews = interviewRepository.findByUserIdAndOutcome(userId, InterviewOutcome.UPCOMING);
        return toDtos(interviews);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getAllInterviews(String email) {
        User user = getUserByEmail(email);
        return getAllInterviews(user.getId());
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getAllInterviews(Long userId) {
        List<Interview> interviews = interviewRepository.findByUserId(userId);
        return toDtos(interviews);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public FeedbackExtractionResult submitFeedback(String email, Long interviewId, String rawFeedback) {
        Long userId = feedbackPersistence.requireOwner(email, interviewId);

        // Gemini AI HTTP call happens outside transaction so DB connections are not starved
        FeedbackExtractionResult result = feedbackExtractionService.extract(rawFeedback);

        feedbackPersistence.save(userId, interviewId, rawFeedback, result);

        return result;
    }

    @Transactional
    public InterviewResponseDto confirmTopics(String email, Long interviewId, ConfirmTopicsRequest request) {
        User user = getUserByEmail(email);
        // Serialize confirmations for this user, including contributions to shared pattern state.
        userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user.getId()));
        Interview interview = interviewRepository.findOwnedByIdForUpdate(user.getId(), interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));

        boolean wasAlreadyConfirmed = interview.getTopicsConfirmedAt() != null;
        if (request == null || request.getTopics() == null) throw new BadRequestException("Topics are required");
        Set<Long> topicIds = new HashSet<>();
        for (ConfirmTopicsRequest.ConfirmedTopicDto topic : request.getTopics()) {
            if (topic == null || topic.getPatternId() == null || topic.getPatternId() <= 0)
                throw new BadRequestException("A positive pattern ID is required for every topic");
            if (!topicIds.add(topic.getPatternId())) throw new BadRequestException("Duplicate pattern topic: " + topic.getPatternId());
        }

        if (request.getOutcome() != null && !request.getOutcome().isBlank()) {
            try {
                InterviewOutcome outcome = InterviewOutcome.valueOf(request.getOutcome().toUpperCase(Locale.ROOT));
                if (outcome != InterviewOutcome.PASSED && outcome != InterviewOutcome.FAILED && outcome != InterviewOutcome.PARTIAL)
                    throw new BadRequestException("Confirmed outcome must be PASSED, FAILED, or PARTIAL");
                interview.setOutcome(outcome);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid interview outcome value: " + request.getOutcome());
            }
        }
        interview.setAiExtracted(true);
        if (!wasAlreadyConfirmed) interview.setTopicsConfirmedAt(timeService.now());
        interviewRepository.saveAndFlush(interview);

        interviewTopicRepository.deleteByInterviewId(interviewId);
        interviewTopicRepository.flush();

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
                    double decayedBoost = interviewRelevanceCalculator.getEffectiveInterviewBoost(state, timeService.getLocalDate(user.getId()));
                    double newBoost = Math.min(policy.getMaxInterviewBoost(), decayedBoost + policy.getTopicConfirmedBoost());
                    state.setInterviewBoost(newBoost);
                    state.setInterviewBoostSetAt(timeService.now());
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
        return interviewRepository.findOwnedById(userId, interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
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
                .topicsConfirmedAt(i.getTopicsConfirmedAt())
                .topics(topicDtos)
                .build();
    }

    // ==================== Application CRUD ====================

    @Transactional
    public ApplicationResponseDto createApplication(String email, ApplicationRequestDto request) {
        User user = getUserByEmail(email);

        Application application = Application.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .role(request.getRole() != null ? request.getRole() : "Software Engineer")
                .status(request.getStatus() != null ? request.getStatus() : ApplicationStatus.APPLIED)
                .appliedDate(request.getAppliedDate() != null ? request.getAppliedDate() : timeService.getLocalDate(user.getId()))
                .notes(request.getNotes())
                .build();

        Application saved = applicationRepository.save(application);
        return toApplicationDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponseDto> getApplications(String email) {
        User user = getUserByEmail(email);
        List<Application> applications = applicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return applications.stream().map(this::toApplicationDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationResponseDto getApplication(String email, Long id) {
        User user = getUserByEmail(email);
        Application application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return toApplicationDto(application);
    }

    @Transactional
    public ApplicationResponseDto updateApplication(String email, Long id, ApplicationRequestDto request) {
        User user = getUserByEmail(email);
        Application application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            application.setCompanyName(request.getCompanyName());
        }
        if (request.getRole() != null) {
            application.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            application.setStatus(request.getStatus());
        }
        if (request.getAppliedDate() != null) {
            application.setAppliedDate(request.getAppliedDate());
        }
        if (request.getNotes() != null) {
            application.setNotes(request.getNotes());
        }

        Application updated = applicationRepository.save(application);
        return toApplicationDto(updated);
    }

    @Transactional
    public void deleteApplication(String email, Long id) {
        User user = getUserByEmail(email);
        Application application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        applicationRepository.delete(application);
    }

    // ==================== Interview CRUD ====================

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getInterviewsForApplication(String email, Long applicationId) {
        User user = getUserByEmail(email);
        applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        List<Interview> interviews = interviewRepository.findByApplicationIdOrderByInterviewDateAsc(applicationId);
        return toDtos(interviews);
    }

    @Transactional
    public InterviewResponseDto createInterviewForApplication(String email, Long applicationId, InterviewRequestDto request) {
        User user = getUserByEmail(email);
        Application application = applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        Interview interview = Interview.builder()
                .application(application)
                .interviewDate(request.getInterviewDate())
                .roundName(request.getRoundName() != null ? request.getRoundName() : "Technical Interview")
                .notes(request.getNotes())
                .outcome(request.getOutcome() != null ? request.getOutcome() : InterviewOutcome.UPCOMING)
                .build();

        Interview saved = interviewRepository.save(interview);

        // Update application status to INTERVIEW if currently WISHLIST/APPLIED/OA
        if (application.getStatus() == ApplicationStatus.WISHLIST ||
            application.getStatus() == ApplicationStatus.APPLIED ||
            application.getStatus() == ApplicationStatus.OA) {
            application.setStatus(ApplicationStatus.INTERVIEW);
            applicationRepository.save(application);
        }

        return toDto(saved);
    }

    @Transactional
    public InterviewResponseDto updateInterview(String email, Long interviewId, InterviewRequestDto request) {
        User user = getUserByEmail(email);
        Interview interview = getInterviewOwnedByUser(user.getId(), interviewId);

        if (request.getInterviewDate() != null) {
            interview.setInterviewDate(request.getInterviewDate());
        }
        if (request.getRoundName() != null) {
            interview.setRoundName(request.getRoundName());
        }
        if (request.getNotes() != null) {
            interview.setNotes(request.getNotes());
        }
        if (request.getOutcome() != null) {
            interview.setOutcome(request.getOutcome());
        }

        Interview updated = interviewRepository.save(interview);
        return toDto(updated);
    }

    @Transactional
    public void deleteInterview(String email, Long interviewId) {
        User user = getUserByEmail(email);
        Interview interview = getInterviewOwnedByUser(user.getId(), interviewId);
        interviewRepository.delete(interview);
    }

    private ApplicationResponseDto toApplicationDto(Application app) {
        List<InterviewResponseDto> interviewDtos = app.getInterviews() != null
                ? toDtos(app.getInterviews())
                : Collections.emptyList();

        return ApplicationResponseDto.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .role(app.getRole())
                .status(app.getStatus())
                .appliedDate(app.getAppliedDate())
                .notes(app.getNotes())
                .interviews(interviewDtos)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
