package com.preptrack.forecast;

import com.preptrack.application.entity.Application;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.common.service.TimeService;
import com.preptrack.config.EngineProperties;
import com.preptrack.forecast.dto.ForecastStatus;
import com.preptrack.forecast.engine.ForwardSimulator;
import com.preptrack.forecast.service.ForecastService;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.engine.InterviewRelevanceCalculator;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ForecastTimezoneTest {
    @Test
    void usesTheUsersLocalDayForInterviewHorizon() {
        var interviews = mock(InterviewRepository.class);
        var settings = mock(UserSettingsRepository.class);
        var simulator = mock(ForwardSimulator.class);
        User user = User.builder().id(1L).build();
        var interview = Interview.builder().id(2L).interviewDate(LocalDate.of(2026, 1, 3))
                .application(Application.builder().user(user).companyName("Example").build()).build();
        when(interviews.findById(2L)).thenReturn(Optional.of(interview));
        when(settings.findByUserId(1L)).thenReturn(Optional.of(UserSettings.builder().user(user).timezone("Asia/Kolkata").build()));
        var service = new ForecastService(interviews, settings, mock(UserPatternStateRepository.class),
                mock(UserSheetRepository.class), mock(SheetProblemRepository.class), mock(ProblemPatternRepository.class),
                mock(UserProblemStatusRepository.class), simulator, mock(InterviewRelevanceCalculator.class),
                new EngineProperties(), new TimeService(Clock.fixed(Instant.parse("2026-01-01T23:30:00Z"), ZoneOffset.UTC)));

        var result = service.getForecastForInterview(1L, 2L);
        assertThat(result.getStatus()).isEqualTo(ForecastStatus.ONBOARDING_REQUIRED);
        assertThat(result.getAsOfDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(result.getDaysRemaining()).isEqualTo(1);
        assertThat(result.getPatternProjections()).isEmpty();
        assertThat(result.getOverallReadiness()).isNull();
        verifyNoInteractions(simulator);
    }
}
