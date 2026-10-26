package com.preptrack.onboarding.service;

import com.preptrack.application.entity.Application;
import com.preptrack.application.entity.ApplicationStatus;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.ApplicationRepository;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.auth.dto.UserSettingsDto;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.onboarding.dto.OnboardingInterviewRequest;
import com.preptrack.onboarding.dto.OnboardingInterviewResponse;
import com.preptrack.onboarding.dto.SheetSelectionRequest;
import com.preptrack.onboarding.dto.SheetSelectionResponse;
import com.preptrack.onboarding.dto.UpdateSettingsRequest;
import com.preptrack.sheet.entity.Sheet;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SheetRepository sheetRepository;
    private final SheetProblemRepository sheetProblemRepository;
    private final UserSheetRepository userSheetRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;

    @Transactional
    public SheetSelectionResponse selectSheets(String email, SheetSelectionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<Sheet> sheets = sheetRepository.findAllById(request.getSheetIds());
        if (sheets.isEmpty()) {
            throw new BadRequestException("No valid sheets found for provided IDs");
        }

        int totalAcrossSheets = 0;
        Set<Long> uniqueProblemIds = new HashSet<>();
        List<String> sheetNames = sheets.stream().map(Sheet::getName).collect(Collectors.toList());

        List<UserSheet> currentSheets = userSheetRepository.findByUserIdWithSheet(user.getId());
        Set<Long> requestedIds = new HashSet<>(request.getSheetIds());
        for (UserSheet us : currentSheets) {
            if (!requestedIds.contains(us.getSheet().getId())) {
                userSheetRepository.delete(us);
            }
        }

        for (Sheet sheet : sheets) {
            if (!userSheetRepository.existsByUserIdAndSheetId(user.getId(), sheet.getId())) {
                UserSheet userSheet = UserSheet.builder()
                        .user(user)
                        .sheet(sheet)
                        .build();
                userSheetRepository.save(userSheet);
            }

            List<SheetProblem> problems = sheetProblemRepository.findBySheetIdOrderByDisplayOrderAsc(sheet.getId());
            totalAcrossSheets += problems.size();
            for (SheetProblem sp : problems) {
                uniqueProblemIds.add(sp.getProblem().getId());
            }
        }

        int uniqueCount = uniqueProblemIds.size();
        int overlapCount = totalAcrossSheets - uniqueCount;

        return SheetSelectionResponse.builder()
                .selectedSheets(sheetNames)
                .totalProblemsAcrossSheets(totalAcrossSheets)
                .uniqueProblems(uniqueCount)
                .overlapProblems(overlapCount)
                .build();
    }

    @Transactional
    public UserSettingsDto updateSettings(String email, UpdateSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        UserSettings settings = userSettingsRepository.findByUserId(user.getId())
                .orElseGet(() -> UserSettings.builder().user(user).build());

        if (request.getDailyTimeBudgetMin() != null) {
            settings.setDailyTimeBudgetMin(request.getDailyTimeBudgetMin());
        }
        if (request.getMaxNewProblemsDay() != null) {
            settings.setMaxNewProblemsDay(request.getMaxNewProblemsDay());
        }
        if (request.getMaxRevisionsDay() != null) {
            settings.setMaxRevisionsDay(request.getMaxRevisionsDay());
        }
        if (request.getTargetSkillLevel() != null) {
            settings.setTargetSkillLevel(request.getTargetSkillLevel());
        }

        settings = userSettingsRepository.save(settings);

        return UserSettingsDto.builder()
                .dailyTimeBudgetMin(settings.getDailyTimeBudgetMin())
                .maxNewProblemsDay(settings.getMaxNewProblemsDay())
                .maxRevisionsDay(settings.getMaxRevisionsDay())
                .targetSkillLevel(settings.getTargetSkillLevel())
                .onboardingComplete(settings.getOnboardingComplete())
                .build();
    }

    @Transactional
    public OnboardingInterviewResponse addInterview(String email, OnboardingInterviewRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Application application = Application.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .role(request.getRole() != null ? request.getRole() : "Software Engineer")
                .status(ApplicationStatus.INTERVIEW)
                .notes(request.getNotes())
                .build();
        application = applicationRepository.save(application);

        Interview interview = Interview.builder()
                .application(application)
                .interviewDate(request.getInterviewDate())
                .roundName("Initial Technical Round")
                .outcome(InterviewOutcome.UPCOMING)
                .rawFeedback(request.getNotes())
                .build();
        interview = interviewRepository.save(interview);

        return OnboardingInterviewResponse.builder()
                .applicationId(application.getId())
                .interviewId(interview.getId())
                .companyName(application.getCompanyName())
                .role(application.getRole())
                .interviewDate(interview.getInterviewDate())
                .outcome(interview.getOutcome().name())
                .notes(request.getNotes())
                .build();
    }

    @Transactional
    public UserSettingsDto completeOnboarding(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        UserSettings settings = userSettingsRepository.findByUserId(user.getId())
                .orElseGet(() -> UserSettings.builder().user(user).build());

        settings.setOnboardingComplete(true);
        settings = userSettingsRepository.save(settings);

        return UserSettingsDto.builder()
                .dailyTimeBudgetMin(settings.getDailyTimeBudgetMin())
                .maxNewProblemsDay(settings.getMaxNewProblemsDay())
                .maxRevisionsDay(settings.getMaxRevisionsDay())
                .targetSkillLevel(settings.getTargetSkillLevel())
                .onboardingComplete(settings.getOnboardingComplete())
                .build();
    }
}
