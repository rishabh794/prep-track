package com.preptrack.attempt.controller;

import com.preptrack.attempt.dto.*;
import com.preptrack.attempt.service.AttemptHistoryService;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.security.UserDetailsImpl;
import com.preptrack.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttemptHistoryController {
    private final AttemptHistoryService history;
    private final UserRepository users;

    @GetMapping("/api/attempts")
    public AttemptHistoryPage getHistory(@AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) Long problemId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return history.getHistory(userId(principal), problemId, page, size);
    }

    @GetMapping("/api/problems/{id}/attempts")
    public AttemptHistoryPage getProblemHistory(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return history.getHistory(userId(principal), id, page, size);
    }

    @GetMapping("/api/problems/{id}/note")
    public ProblemNoteDto getNote(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return history.getNote(userId(principal), id);
    }

    @PutMapping("/api/problems/{id}/note")
    public ProblemNoteDto updateNote(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @Valid @RequestBody UpdateProblemNoteRequest request) {
        return history.updateNote(userId(principal), id, request.insightNote());
    }

    private Long userId(UserDetails principal) {
        if (principal instanceof UserDetailsImpl user) return user.getId();
        return users.findByEmail(principal.getUsername()).orElseThrow(() -> new ResourceNotFoundException("User not found")).getId();
    }
}
