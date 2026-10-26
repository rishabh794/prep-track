package com.preptrack.sheet.service;

import com.preptrack.attempt.entity.ProblemStatus;

import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.dto.PatternSummaryDto;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.sheet.dto.AvailableSheetResponse;
import com.preptrack.sheet.dto.SheetProblemResponse;
import com.preptrack.sheet.dto.UserSheetResponse;
import com.preptrack.sheet.entity.Sheet;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.SheetRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SheetService {

    private final SheetRepository sheetRepository;
    private final SheetProblemRepository sheetProblemRepository;
    private final UserSheetRepository userSheetRepository;
    private final UserRepository userRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final UserProblemStatusRepository userProblemStatusRepository;

    @Transactional(readOnly = true)
    public List<AvailableSheetResponse> getAvailableSheets() {
        List<Sheet> sheets = sheetRepository.findByIsActiveTrue();
        return sheets.stream().map(s -> AvailableSheetResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .sourceUrl(s.getSourceUrl())
                .totalProblems(sheetProblemRepository.countBySheetId(s.getId()))
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSheetResponse> getUserSheets(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(user.getId());

        List<UserProblemStatus> solvedStatuses = userProblemStatusRepository.findByUserIdAndStatusIn(
                user.getId(),
                List.of(ProblemStatus.SOLVED_INDEPENDENTLY, ProblemStatus.SOLVED_WITH_HELP)
        );
        Set<Long> solvedProblemIds = solvedStatuses.stream()
                .map(s -> s.getProblem().getId())
                .collect(Collectors.toSet());

        return userSheets.stream().map(us -> {
            Sheet sheet = us.getSheet();
            List<SheetProblem> problems = sheetProblemRepository.findBySheetIdOrderByDisplayOrderAsc(sheet.getId());
            long total = problems.size();
            long solved = problems.stream()
                    .filter(sp -> solvedProblemIds.contains(sp.getProblem().getId()))
                    .count();
            double progressPercent = total > 0 ? Math.round(((double) solved / total) * 1000.0) / 10.0 : 0.0;

            return UserSheetResponse.builder()
                    .sheetId(sheet.getId())
                    .sheetName(sheet.getName())
                    .description(sheet.getDescription())
                    .totalProblems(total)
                    .solvedProblems(solved)
                    .progressPercent(progressPercent)
                    .selectedAt(us.getSelectedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SheetProblemResponse> getSheetProblems(Long sheetId, String email) {
        Sheet sheet = sheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Sheet not found with id: " + sheetId));

        User user = userRepository.findByEmail(email).orElse(null);

        List<SheetProblem> sheetProblems = sheetProblemRepository.findBySheetIdOrderByDisplayOrderAsc(sheetId);
        if (sheetProblems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> problemIds = sheetProblems.stream()
                .map(sp -> sp.getProblem().getId())
                .collect(Collectors.toList());

        Map<Long, ProblemStatus> statusMap = new HashMap<>();
        if (user != null) {
            List<UserProblemStatus> userStatuses = userProblemStatusRepository.findByUserIdAndProblemIdIn(user.getId(), problemIds);
            for (UserProblemStatus ups : userStatuses) {
                statusMap.put(ups.getProblem().getId(), ups.getStatus());
            }
        }

        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdInWithPattern(problemIds);
        Map<Long, List<PatternSummaryDto>> patternsByProblemId = new HashMap<>();

        for (ProblemPattern pp : problemPatterns) {
            patternsByProblemId.computeIfAbsent(pp.getProblem().getId(), k -> new ArrayList<>())
                    .add(PatternSummaryDto.builder()
                            .id(pp.getPattern().getId())
                            .name(pp.getPattern().getName())
                            .parentTopic(pp.getPattern().getParentTopic())
                            .build());
        }

        return sheetProblems.stream().map(sp -> {
            ProblemStatus status = statusMap.get(sp.getProblem().getId());
            String statusStr = status != null ? status.name() : "NOT_STARTED";

            return SheetProblemResponse.builder()
                    .id(sp.getProblem().getId())
                    .canonicalTitle(sp.getProblem().getCanonicalTitle())
                    .difficulty(sp.getProblem().getDifficulty().name())
                    .displayOrder(sp.getDisplayOrder())
                    .category(sp.getCategory())
                    .status(statusStr)
                    .patterns(patternsByProblemId.getOrDefault(sp.getProblem().getId(), Collections.emptyList()))
                    .build();
        }).collect(Collectors.toList());
    }
}

