package com.eyelanding.fundamentalengine.api.controller;

import com.eyelanding.fundamentalengine.api.dto.ScreenerResponse;
import com.eyelanding.fundamentalengine.application.screener.FaScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * GET /internal/fa/screener
 *
 * Query params:
 *   period    = 2026Q1 (default)
 *   rating    = STRONG_FA | GOOD_FA | FAIR_FA | WEAK_FA | POOR_FA
 *   minScore  = 0-100
 *   exchange  = HOSE | HNX | UPCOM
 *   batchId   = specific import batch
 *   page      = 0-indexed (default 0)
 *   pageSize  = items per page (default 50, max 100)
 */
@RestController
@RequestMapping("/internal/fa/screener")
@RequiredArgsConstructor
@Tag(name = "FA Screener", description = "Screen tickers by fundamental analysis criteria")
public class FaScreenerController {

    private final FaScreenerService screenerService;

    @GetMapping
    @Operation(summary = "Screen tickers by FA score, rating, and exchange")
    public ResponseEntity<ScreenerResponse> screen(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) Long batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {

        return ResponseEntity.ok(
                screenerService.screen(period, rating, minScore, exchange, batchId, page, pageSize));
    }
}
