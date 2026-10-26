package com.preptrack.planner.controller;

import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.planner.dto.DailyPlanResponse;
import com.preptrack.planner.dto.SkipItemRequest;
import com.preptrack.planner.service.PlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;
    private final UserRepository userRepository;

    @GetMapping("/today")
    public ResponseEntity<DailyPlanResponse> getTodayPlan(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        DailyPlanResponse response = plannerService.getOrCreatePlanForToday(user.getId(), LocalDate.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{date}")
    public ResponseEntity<DailyPlanResponse> getPlanByDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String date
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        LocalDate targetDate = LocalDate.parse(date);
        DailyPlanResponse response = plannerService.getOrCreatePlanForToday(user.getId(), targetDate);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/today/regenerate")
    public ResponseEntity<DailyPlanResponse> regenerateTodayPlan(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        DailyPlanResponse response = plannerService.regeneratePlan(user.getId(), LocalDate.now());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/items/{itemId}/skip")
    public ResponseEntity<DailyPlanResponse> skipPlanItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId,
            @RequestBody(required = false) SkipItemRequest request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        String reason = request != null && request.getReason() != null ? request.getReason() : "Skipped by user";
        DailyPlanResponse response = plannerService.skipPlanItem(user.getId(), itemId, reason);
        return ResponseEntity.ok(response);
    }
}
