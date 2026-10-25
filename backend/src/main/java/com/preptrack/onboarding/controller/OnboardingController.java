package com.preptrack.onboarding.controller;

import com.preptrack.auth.dto.UserSettingsDto;
import com.preptrack.onboarding.dto.OnboardingInterviewRequest;
import com.preptrack.onboarding.dto.OnboardingInterviewResponse;
import com.preptrack.onboarding.dto.SheetSelectionRequest;
import com.preptrack.onboarding.dto.SheetSelectionResponse;
import com.preptrack.onboarding.dto.UpdateSettingsRequest;
import com.preptrack.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/sheets")
    public ResponseEntity<SheetSelectionResponse> selectSheets(
            @Valid @RequestBody SheetSelectionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(onboardingService.selectSheets(userDetails.getUsername(), request));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsDto> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(onboardingService.updateSettings(userDetails.getUsername(), request));
    }

    @PostMapping("/interviews")
    public ResponseEntity<OnboardingInterviewResponse> addInterview(
            @Valid @RequestBody OnboardingInterviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.addInterview(userDetails.getUsername(), request));
    }

    @PostMapping("/complete")
    public ResponseEntity<UserSettingsDto> completeOnboarding(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(onboardingService.completeOnboarding(userDetails.getUsername()));
    }
}
