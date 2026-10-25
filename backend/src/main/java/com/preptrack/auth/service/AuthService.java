package com.preptrack.auth.service;

import com.preptrack.auth.dto.*;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.auth.security.JwtService;
import com.preptrack.auth.security.UserDetailsImpl;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName().trim())
                .build();
        user = userRepository.save(user);

        UserSettings settings = UserSettings.builder()
                .user(user)
                .dailyTimeBudgetMin(90)
                .maxNewProblemsDay(3)
                .maxRevisionsDay(10)
                .targetSkillLevel(0.8)
                .onboardingComplete(false)
                .build();
        userSettingsRepository.save(settings);
        user.setSettings(settings);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .onboardingComplete(false)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        boolean onboardingComplete = settings != null && Boolean.TRUE.equals(settings.getOnboardingComplete());

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .onboardingComplete(onboardingComplete)
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);

        UserSettingsDto settingsDto = null;
        if (settings != null) {
            settingsDto = UserSettingsDto.builder()
                    .dailyTimeBudgetMin(settings.getDailyTimeBudgetMin())
                    .maxNewProblemsDay(settings.getMaxNewProblemsDay())
                    .maxRevisionsDay(settings.getMaxRevisionsDay())
                    .targetSkillLevel(settings.getTargetSkillLevel())
                    .onboardingComplete(settings.getOnboardingComplete())
                    .build();
        }

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .settings(settingsDto)
                .build();
    }
}
