package com.preptrack.company;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.company.service.CompanyProfileSeedService;
import com.preptrack.company.service.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Order(10)
@RequiredArgsConstructor
public class CompanyProfileSeedLoader implements CommandLineRunner {
    private final CompanyProfileSeedService seeds;
    private final CompanyProfileService profiles;
    private final ObjectMapper mapper;
    @Value("${preptrack.seed.enabled:true}") private boolean seedEnabled;

    public void run(String... args) throws Exception {
        if (seedEnabled) {
            byte[] bytes;
            try (var input = new ClassPathResource("seed/company-profiles.json").getInputStream()) {
                bytes = input.readAllBytes();
            }
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(com.preptrack.seed.SeedChecksums.canonicalJson(mapper, bytes)));
            seeds.synchronize(mapper.readValue(bytes, CompanyProfileSeedService.SeedFile.class), checksum);
        }
        // Refresh after the seed transaction commits, including when seeding is disabled on a populated database.
        profiles.refresh();
    }
}
