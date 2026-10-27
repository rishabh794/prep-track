package com.preptrack.company.controller;

import com.preptrack.company.dto.CompanyProfileDto;
import com.preptrack.company.service.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyProfileController {
    private final CompanyProfileService profiles;
    public record Resolution(boolean matched, double defaultWeight, CompanyProfileDto profile) {}

    @GetMapping
    public List<CompanyProfileDto> list() { return profiles.list(); }

    @GetMapping("/resolve")
    public Resolution resolve(@RequestParam String name) {
        var profile = profiles.resolve(name);
        return new Resolution(profile.isPresent(), 1.0, profile.orElse(null));
    }

    @GetMapping("/{slug}")
    public CompanyProfileDto get(@PathVariable String slug) { return profiles.get(slug); }
}
