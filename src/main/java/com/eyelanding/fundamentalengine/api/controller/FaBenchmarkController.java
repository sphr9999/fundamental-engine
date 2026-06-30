package com.eyelanding.fundamentalengine.api.controller;

import com.eyelanding.fundamentalengine.api.dto.IndustryBenchmarkResponse;
import com.eyelanding.fundamentalengine.application.screener.IndustryBenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/fa/benchmarks")
@RequiredArgsConstructor
@Tag(name = "FA Benchmarks", description = "Industry-level FA benchmark data")
public class FaBenchmarkController {

    private final IndustryBenchmarkService benchmarkService;

    /**
     * GET /internal/fa/benchmarks/industry
     * Returns median PE/PB/margins/YoY growth and FA score grouped by industry (ngành).
     */
    @GetMapping("/industry")
    @Operation(summary = "Get industry benchmarks",
            description = "Median PE, PB, net margin, gross margin, revenue YoY, NPAT YoY and FA score by industry")
    public ResponseEntity<IndustryBenchmarkResponse> getIndustryBenchmarks(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long batchId) {
        return ResponseEntity.ok(benchmarkService.getBenchmarks(period, batchId));
    }
}
