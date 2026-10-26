package com.preptrack.revision.controller;

import com.preptrack.ai.dto.RecallCheckResponseDto;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.revision.dto.RevisionCardDto;
import com.preptrack.revision.service.RevisionService;
import com.preptrack.revision.dto.ReviewRevisionRequest;
import com.preptrack.revision.dto.SubmitRecallRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/revisions")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;
    private final UserRepository userRepository;

    @GetMapping("/due")
    public ResponseEntity<List<RevisionCardDto>> getDueRevisions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String date
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        List<RevisionCardDto> due = revisionService.getDueRevisions(user.getId(), targetDate);
        return ResponseEntity.ok(due);
    }

    @PostMapping("/{cardId}/recall")
    public ResponseEntity<RecallCheckResponseDto> submitRecall(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @RequestBody @Valid SubmitRecallRequest body
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        String userApproach = body.getUserApproach();
        RecallCheckResponseDto response = revisionService.submitRecallCheck(user.getId(), cardId, userApproach);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{cardId}/review")
    public ResponseEntity<RevisionCardDto> reviewRevision(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @RequestBody @Valid ReviewRevisionRequest body
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        RevisionCardDto updated = revisionService.reviewRevisionWithRecall(user.getId(), cardId, body);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/problem/{problemId}/recall")
    public ResponseEntity<RecallCheckResponseDto> submitRecallByProblem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long problemId,
            @RequestBody @Valid SubmitRecallRequest body
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        String userApproach = body.getUserApproach();
        RecallCheckResponseDto response = revisionService.submitRecallCheckByProblemId(user.getId(), problemId, userApproach);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/problem/{problemId}/review")
    public ResponseEntity<RevisionCardDto> reviewRevisionByProblem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long problemId,
            @RequestBody @Valid ReviewRevisionRequest body
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        RevisionCardDto updated = revisionService.reviewRevisionByProblemIdWithRecall(user.getId(), problemId, body);
        return ResponseEntity.ok(updated);
    }
}
