package com.preptrack.problem.service;

import com.preptrack.problem.dto.ProblemRelationDto;
import com.preptrack.problem.entity.ProblemRelation;
import com.preptrack.problem.entity.RelationType;
import com.preptrack.problem.repository.ProblemRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelationService {

    private final ProblemRelationRepository problemRelationRepository;

    @Transactional(readOnly = true)
    public List<ProblemRelationDto> getRelationsForProblem(Long problemId) {
        List<ProblemRelation> relations = problemRelationRepository.findBySourceProblemIdWithTarget(problemId);
        return relations.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemRelationDto> getEasierPrerequisites(Long problemId) {
        List<ProblemRelation> relations = problemRelationRepository.findBySourceProblemIdAndRelationType(
                problemId, RelationType.EASIER_PREREQUISITE
        );
        return relations.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private ProblemRelationDto mapToDto(ProblemRelation relation) {
        return ProblemRelationDto.builder()
                .relatedProblemId(relation.getTargetProblem().getId())
                .relatedProblemTitle(relation.getTargetProblem().getCanonicalTitle())
                .relationType(relation.getRelationType().name())
                .notes(relation.getNotes())
                .build();
    }
}
