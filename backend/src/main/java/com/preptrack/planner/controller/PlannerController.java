package com.preptrack.planner.controller;

import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.security.UserDetailsImpl;
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

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof UserDetailsImpl impl) {
            return impl.getId();
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()))
                .getId();
    }

    @GetMapping("/today")
    public ResponseEntity<DailyPlanResponse> getTodayPlan(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        DailyPlanResponse response = plannerService.getOrCreatePlanForToday(getUserId(userDetails), null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{date}")
    public ResponseEntity<DailyPlanResponse> getPlanByDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String date
    ) {
        LocalDate targetDate = LocalDate.parse(date);
        DailyPlanResponse response = plannerService.getPlanByDate(getUserId(userDetails), targetDate);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/today/regenerate")
    public ResponseEntity<DailyPlanResponse> regenerateTodayPlan(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        DailyPlanResponse response = plannerService.regeneratePlan(getUserId(userDetails), null);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/items/{itemId}/skip")
    public ResponseEntity<DailyPlanResponse> skipPlanItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId,
            @RequestBody(required = false) SkipItemRequest request
    ) {
        String reason = request != null && request.getReason() != null ? request.getReason() : "Skipped by user";
        DailyPlanResponse response = plannerService.skipPlanItem(getUserId(userDetails), itemId, reason);
        return ResponseEntity.ok(response);
    }
}
