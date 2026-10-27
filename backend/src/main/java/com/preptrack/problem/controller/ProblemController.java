package com.preptrack.problem.controller;

import com.preptrack.problem.dto.ProblemDetailResponse;
import com.preptrack.problem.dto.ProblemRelationDto;
import com.preptrack.problem.dto.ProblemSourceDto;
import com.preptrack.problem.service.ProblemService;
import com.preptrack.problem.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.preptrack.attempt.service.AttemptHistoryService;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.security.UserDetailsImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.preptrack.common.exception.ResourceNotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final RelationService relationService;
    private final AttemptHistoryService historyService;
    private final UserRepository users;

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> getProblemDetail(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        Long userId = principal instanceof UserDetailsImpl user ? user.getId()
                : users.findByEmail(principal.getUsername()).orElseThrow(() -> new ResourceNotFoundException("User not found")).getId();
        var detail = problemService.getProblemDetail(id);
        detail.setInsightNote(historyService.getNote(userId, id).insightNote());
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{id}/sources")
    public ResponseEntity<List<ProblemSourceDto>> getProblemSources(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getProblemSources(id));
    }

    @GetMapping("/{id}/relations")
    public ResponseEntity<List<ProblemRelationDto>> getProblemRelations(@PathVariable Long id) {
        return ResponseEntity.ok(relationService.getRelationsForProblem(id));
    }
}
