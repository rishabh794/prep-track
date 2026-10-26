package com.preptrack.ai.controller;

import com.preptrack.ai.dto.FeedbackExtractionResult;
import com.preptrack.ai.service.FeedbackExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final FeedbackExtractionService feedbackExtractionService;

    @PostMapping("/extract-feedback")
    public ResponseEntity<FeedbackExtractionResult> extractFeedback(@RequestBody Map<String, String> body) {
        String rawFeedback = body.get("rawFeedback");
        return ResponseEntity.ok(feedbackExtractionService.extract(rawFeedback));
    }
}
