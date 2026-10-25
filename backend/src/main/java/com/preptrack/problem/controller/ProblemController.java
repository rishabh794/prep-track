package com.preptrack.problem.controller;

import com.preptrack.problem.dto.ProblemDetailResponse;
import com.preptrack.problem.dto.ProblemRelationDto;
import com.preptrack.problem.dto.ProblemSourceDto;
import com.preptrack.problem.service.ProblemService;
import com.preptrack.problem.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final RelationService relationService;

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> getProblemDetail(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getProblemDetail(id));
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
