package com.preptrack.application.controller;

import com.preptrack.ai.dto.ConfirmTopicsRequest;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.dto.InterviewTopicDto;
import com.preptrack.application.service.ApplicationService;
import com.preptrack.auth.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.preptrack.ai.dto.ExtractFeedbackRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<InterviewResponseDto>> getAllInterviews(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails instanceof UserDetailsImpl impl) {
            return ResponseEntity.ok(applicationService.getAllInterviews(impl.getId()));
        }
        return ResponseEntity.ok(applicationService.getAllInterviews(userDetails.getUsername()));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<InterviewResponseDto>> getUpcomingInterviews(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails instanceof UserDetailsImpl impl) {
            return ResponseEntity.ok(applicationService.getUpcomingInterviews(impl.getId()));
        }
        return ResponseEntity.ok(applicationService.getUpcomingInterviews(userDetails.getUsername()));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<FeedbackExtractionResult> submitFeedback(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ExtractFeedbackRequest request
    ) {
        FeedbackExtractionResult result = applicationService.submitFeedback(userDetails.getUsername(), id, request.getRawFeedback());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/confirm-topics")
    public ResponseEntity<InterviewResponseDto> confirmTopics(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody ConfirmTopicsRequest request
    ) {
        InterviewResponseDto response = applicationService.confirmTopics(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/topics")
    public ResponseEntity<List<InterviewTopicDto>> getTopics(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(applicationService.getInterviewTopics(userDetails.getUsername(), id));
    }
}
