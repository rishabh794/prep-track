package com.preptrack.forecast.controller;

import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.security.UserDetailsImpl;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.forecast.dto.ReadinessForecastDto;
import com.preptrack.forecast.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;
    private final UserRepository userRepository;

    @GetMapping("/interviews/{interviewId}")
    public ResponseEntity<ReadinessForecastDto> getForecast(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long interviewId
    ) {
        Long userId = (userDetails instanceof UserDetailsImpl impl) ? impl.getId()
                : userRepository.findByEmail(userDetails.getUsername())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()))
                        .getId();

        ReadinessForecastDto forecast = forecastService.getForecastForInterview(userId, interviewId);
        return ResponseEntity.ok(forecast);
    }
}
