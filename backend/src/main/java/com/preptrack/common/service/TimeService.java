package com.preptrack.common.service;

import com.preptrack.auth.entity.UserSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TimeService {

    private final Clock clock;

    public LocalDate getLocalDate(UserSettings settings) {
        String tz = (settings != null && settings.getTimezone() != null && !settings.getTimezone().isBlank())
                ? settings.getTimezone()
                : "UTC";
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(tz)));
        } catch (Exception e) {
            return LocalDate.now(clock);
        }
    }

    public LocalDate getLocalDate(String timezone) {
        try {
            ZoneId zoneId = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.systemDefault();
            return LocalDate.now(clock.withZone(zoneId));
        } catch (Exception e) {
            return LocalDate.now(clock);
        }
    }
}
