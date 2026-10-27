package com.preptrack.catalog.controller;

import com.preptrack.catalog.dto.CatalogHealthResponse;
import com.preptrack.catalog.service.CatalogHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogHealthService health;
    @GetMapping("/health")
    public CatalogHealthResponse health() { return health.inspect(); }
}
