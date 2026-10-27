package com.preptrack.common.service;

import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class TimeService {

    private final Clock clock;
    private final UserSettingsRepository settingsRepository;

    @Autowired
    public TimeService(Clock clock, UserSettingsRepository settingsRepository) {
        this.clock = clock;
        this.settingsRepository = settingsRepository;
    }

    public TimeService(Clock clock) { this(clock, null); }

    public LocalDate getLocalDate(Long userId) {
        return getLocalDate(settingsFor(userId));
    }

    /** Persist timestamps on the server clock, independent of changes to user preferences. */
    public LocalDateTime now() { return LocalDateTime.now(clock); }

    public LocalDateTime getLocalDateTime(Long userId) {
        return LocalDateTime.now(clock.withZone(zone(settingsFor(userId))));
    }

    public LocalDate getPracticeDate(LocalDateTime timestamp, Long userId) {
        return getPracticeDate(timestamp, settingsFor(userId));
    }

    public LocalDate getPracticeDate(LocalDateTime timestamp, UserSettings settings) {
        return timestamp.atZone(clock.getZone()).withZoneSameInstant(zone(settings)).toLocalDate();
    }

    public java.time.Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(clock.getZone()).toInstant();
    }

    private UserSettings settingsFor(Long userId) {
        if (settingsRepository == null || userId == null) return null;
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes == null) return settingsRepository.findByUserId(userId).orElse(null);
        String key = TimeService.class.getName() + ".settings." + userId;
        Object cached = attributes.getAttribute(key, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof java.util.Optional<?> value) return (UserSettings) value.orElse(null);
        var settings = settingsRepository.findByUserId(userId);
        attributes.setAttribute(key, settings, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return settings.orElse(null);
    }

    public void invalidateCalendar(Long userId) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes != null) attributes.removeAttribute(TimeService.class.getName() + ".settings." + userId,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
    }

    private ZoneId zone(UserSettings settings) {
        try {
            return ZoneId.of(settings == null || settings.getTimezone() == null || settings.getTimezone().isBlank() ? "UTC" : settings.getTimezone());
        } catch (java.time.DateTimeException ex) { return ZoneId.of("UTC"); }
    }

    public LocalDate getLocalDate(UserSettings settings) {
        return LocalDate.now(clock.withZone(zone(settings)));
    }

    public LocalDate getLocalDate(String timezone) {
        try {
            ZoneId zoneId = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("UTC");
            return LocalDate.now(clock.withZone(zoneId));
        } catch (Exception e) {
            return LocalDate.now(clock.withZone(ZoneId.of("UTC")));
        }
    }
}
