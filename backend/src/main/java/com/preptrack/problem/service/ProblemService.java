package com.preptrack.problem.service;

import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.dto.PatternSummaryDto;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.problem.dto.ProblemDetailResponse;
import com.preptrack.problem.dto.ProblemRelationDto;
import com.preptrack.problem.dto.ProblemSourceDto;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.entity.ProblemSource;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.problem.repository.ProblemSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemSourceRepository problemSourceRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final RelationService relationService;

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemDetail(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        List<ProblemSource> sources = problemSourceRepository.findByProblemId(id);
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(id);
        List<ProblemRelationDto> relations = relationService.getRelationsForProblem(id);

        List<ProblemSourceDto> sourceDtos = sources.stream()
                .map(s -> ProblemSourceDto.builder()
                        .platform(s.getPlatform().name())
                        .externalId(s.getExternalId())
                        .externalUrl(s.getExternalUrl())
                        .externalTitle(s.getExternalTitle())
                        .build())
                .collect(Collectors.toList());

        List<PatternSummaryDto> patternDtos = problemPatterns.stream()
                .map(pp -> PatternSummaryDto.builder()
                        .id(pp.getPattern().getId())
                        .name(pp.getPattern().getName())
                        .parentTopic(pp.getPattern().getParentTopic())
                        .build())
                .collect(Collectors.toList());

        return ProblemDetailResponse.builder()
                .id(problem.getId())
                .canonicalTitle(problem.getCanonicalTitle())
                .difficulty(problem.getDifficulty().name())
                .referenceApproach(problem.getReferenceApproach())
                .optimalComplexity(problem.getOptimalComplexity())
                .patterns(patternDtos)
                .sources(sourceDtos)
                .relations(relations)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProblemSourceDto> getProblemSources(Long id) {
        if (!problemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Problem not found with id: " + id);
        }
        return problemSourceRepository.findByProblemId(id).stream()
                .map(s -> ProblemSourceDto.builder()
                        .platform(s.getPlatform().name())
                        .externalId(s.getExternalId())
                        .externalUrl(s.getExternalUrl())
                        .externalTitle(s.getExternalTitle())
                        .build())
                .collect(Collectors.toList());
    }
}
