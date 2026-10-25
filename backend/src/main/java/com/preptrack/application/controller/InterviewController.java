package com.preptrack.application.controller;

import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final ApplicationService applicationService;

    @GetMapping("/upcoming")
    public ResponseEntity<List<InterviewResponseDto>> getUpcomingInterviews(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(applicationService.getUpcomingInterviews(userDetails.getUsername()));
    }
}
