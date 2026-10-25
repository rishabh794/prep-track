package com.preptrack.application.service;

import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.ApplicationRepository;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<InterviewResponseDto> getUpcomingInterviews(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<Interview> interviews = interviewRepository.findByUserIdAndOutcome(user.getId(), InterviewOutcome.UPCOMING);

        return interviews.stream().map(i -> InterviewResponseDto.builder()
                .id(i.getId())
                .applicationId(i.getApplication().getId())
                .companyName(i.getApplication().getCompanyName())
                .role(i.getApplication().getRole())
                .interviewDate(i.getInterviewDate())
                .roundName(i.getRoundName())
                .outcome(i.getOutcome().name())
                .notes(i.getRawFeedback())
                .build()
        ).collect(Collectors.toList());
    }
}
