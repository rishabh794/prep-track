package com.preptrack.pattern.controller;

import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.dto.PatternStateResponseDto;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.service.PatternStateService;
import com.preptrack.planner.engine.RetentionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PatternController {

    private final PatternRepository patternRepository;
    private final PatternStateService patternStateService;
    private final RetentionCalculator retentionCalculator;
    private final UserRepository userRepository;

    @GetMapping("/patterns")
    public ResponseEntity<List<Pattern>> getAllPatterns() {
        return ResponseEntity.ok(patternRepository.findAll());
    }

    @GetMapping("/user/pattern-states")
    public ResponseEntity<List<PatternStateResponseDto>> getUserPatternStates(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));

        List<Pattern> allPatterns = patternRepository.findAll();
        List<UserPatternState> states = patternStateService.getUserPatternStates(user.getId());
        Map<Long, UserPatternState> stateMap = states.stream()
                .collect(Collectors.toMap(s -> s.getPattern().getId(), s -> s));

        List<PatternStateResponseDto> result = allPatterns.stream().map(p -> {
            UserPatternState state = stateMap.get(p.getId());
            double skill = state != null && state.getSkill() != null ? state.getSkill() : 0.0;
            double stability = state != null && state.getStability() != null ? state.getStability() : 1.0;
            double retention = retentionCalculator.compute(state != null ? state.getLastPracticedAt() : null, stability);

            return PatternStateResponseDto.builder()
                    .patternId(p.getId())
                    .name(p.getName())
                    .slug(p.getSlug())
                    .parentTopic(p.getParentTopic())
                    .skill(Math.round(skill * 100.0) / 100.0)
                    .retention(Math.round(retention * 100.0) / 100.0)
                    .stability(Math.round(stability * 10.0) / 10.0)
                    .lastPracticedAt(state != null ? state.getLastPracticedAt() : null)
                    .totalAttempts(state != null && state.getTotalAttempts() != null ? state.getTotalAttempts() : 0)
                    .independentSolves(state != null && state.getIndependentSolves() != null ? state.getIndependentSolves() : 0)
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }
}
