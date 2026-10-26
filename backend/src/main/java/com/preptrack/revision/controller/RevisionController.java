package com.preptrack.revision.controller;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.revision.dto.RevisionCardDto;
import com.preptrack.revision.service.RevisionService;
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

    @PostMapping("/{cardId}/review")
    public ResponseEntity<RevisionCardDto> reviewRevision(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @RequestBody Map<String, String> body
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        AttemptOutcome outcome = AttemptOutcome.valueOf(body.get("outcome"));
        RevisionCardDto updated = revisionService.reviewRevision(user.getId(), cardId, outcome);
        return ResponseEntity.ok(updated);
    }
}
