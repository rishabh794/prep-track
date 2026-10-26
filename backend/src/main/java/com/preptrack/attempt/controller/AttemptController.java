package com.preptrack.attempt.controller;

import com.preptrack.attempt.dto.AttemptResponseDto;
import com.preptrack.attempt.dto.CreateAttemptRequest;
import com.preptrack.attempt.service.AttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping
    public ResponseEntity<AttemptResponseDto> recordAttempt(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAttemptRequest request
    ) {
        AttemptResponseDto response = attemptService.recordAttempt(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }
}
