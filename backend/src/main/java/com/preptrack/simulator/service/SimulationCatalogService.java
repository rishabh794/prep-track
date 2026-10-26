package com.preptrack.simulator.service;

import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import com.preptrack.simulator.engine.SimulationCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationCatalogService {
    private final SheetRepository sheets;
    private final SheetProblemRepository sheetProblems;
    private final ProblemPatternRepository problemPatterns;

    @Transactional(readOnly = true)
    public SimulationCatalog load(Long sheetId) {
        var sheet = (sheetId == null ? sheets.findByName("NeetCode 150") : sheets.findById(sheetId))
                .orElseThrow(() -> new ResourceNotFoundException("Simulation sheet not found; load seed data or choose a valid sheet"));
        var problems = sheetProblems.findBySheetIdOrderByDisplayOrderAsc(sheet.getId()).stream()
                .map(sp -> sp.getProblem()).toList();
        if (problems.isEmpty() || problems.size() > 500) throw new BadRequestException("Simulation requires a sheet with 1–500 problems");
        Map<Long, List<ProblemPattern>> links = problemPatterns.findByProblemIdInWithPattern(problems.stream().map(p -> p.getId()).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getProblem().getId()));
        if (problems.stream().anyMatch(p -> !links.containsKey(p.getId()) || p.getEstimatedMinutes() == null || p.getEstimatedMinutes() <= 0)) {
            throw new BadRequestException("Every simulation problem needs pattern links and a positive time estimate");
        }
        // Stable order is essential for tie-breaking and repeatable seeded reports.
        links.values().forEach(list -> list.sort(Comparator.comparing(p -> p.getPattern().getId())));
        var patterns = links.values().stream().flatMap(Collection::stream).map(ProblemPattern::getPattern)
                .collect(Collectors.toMap(p -> p.getId(), p -> p, (a, b) -> a, TreeMap::new)).values().stream().toList();
        return new SimulationCatalog(sheet.getName(), List.copyOf(problems), patterns, links);
    }
}
