package com.preptrack.pattern.controller;

import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.dto.PatternStateResponseDto;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.pattern.service.PatternStateService;
import com.preptrack.auth.security.UserDetailsImpl;
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
    private final UserRepository userRepository;

    @GetMapping("/patterns")
    public ResponseEntity<List<com.preptrack.pattern.dto.PatternDto>> getAllPatterns() {
        List<com.preptrack.pattern.dto.PatternDto> result = patternRepository.findAll().stream()
                .map(p -> com.preptrack.pattern.dto.PatternDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .slug(p.getSlug())
                        .parentTopic(p.getParentTopic())
                        .description(p.getDescription())
                        .difficultyTier(p.getDifficultyTier())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/pattern-states")
    public ResponseEntity<List<PatternStateResponseDto>> getUserPatternStates(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = (userDetails instanceof UserDetailsImpl impl) ? impl.getId()
                : userRepository.findByEmail(userDetails.getUsername())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()))
                        .getId();

        return ResponseEntity.ok(patternStateService.getUserPatternStateDtos(userId));
    }
}
