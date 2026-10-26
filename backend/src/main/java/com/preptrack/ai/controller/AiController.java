package com.preptrack.ai.controller;

import com.preptrack.ai.dto.ExtractFeedbackRequest;
import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.ai.service.FeedbackExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final FeedbackExtractionService feedbackExtractionService;

    @PostMapping("/extract-feedback")
    public ResponseEntity<FeedbackExtractionResult> extractFeedback(@Valid @RequestBody ExtractFeedbackRequest request) {
        return ResponseEntity.ok(feedbackExtractionService.extract(request.getRawFeedback()));
    }
}
