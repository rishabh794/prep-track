package com.preptrack.application.controller;

import com.preptrack.application.dto.ApplicationRequestDto;
import com.preptrack.application.dto.ApplicationResponseDto;
import com.preptrack.application.dto.InterviewRequestDto;
import com.preptrack.application.dto.InterviewResponseDto;
import com.preptrack.application.service.ApplicationService;
import com.preptrack.auth.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponseDto>> getApplications(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(applicationService.getApplications(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponseDto> createApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ApplicationRequestDto request
    ) {
        ApplicationResponseDto response = applicationService.createApplication(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponseDto> getApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(applicationService.getApplication(userDetails.getUsername(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponseDto> updateApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationRequestDto request
    ) {
        return ResponseEntity.ok(applicationService.updateApplication(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        applicationService.deleteApplication(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/interviews")
    public ResponseEntity<List<InterviewResponseDto>> getInterviewsForApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(applicationService.getInterviewsForApplication(userDetails.getUsername(), id));
    }

    @PostMapping("/{id}/interviews")
    public ResponseEntity<InterviewResponseDto> createInterviewForApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody InterviewRequestDto request
    ) {
        InterviewResponseDto response = applicationService.createInterviewForApplication(userDetails.getUsername(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
