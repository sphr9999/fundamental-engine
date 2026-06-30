package com.eyelanding.fundamentalengine.api.controller;

import com.eyelanding.fundamentalengine.api.dto.TickerFinancialsResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerOverviewResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerRatiosResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerScoreHistoryResponse;
import com.eyelanding.fundamentalengine.application.ticker.TickerFaQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/fa/tickers")
@RequiredArgsConstructor
@Tag(name = "Ticker FA", description = "Fundamental analysis data per ticker")
public class TickerFaController {

    private final TickerFaQueryService queryService;

    /** GET /internal/fa/tickers/{ticker}/overview */
    @GetMapping("/{ticker}/overview")
    @Operation(summary = "Get FA overview for a ticker")
    public ResponseEntity<TickerOverviewResponse> getOverview(
            @PathVariable String ticker,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long batchId) {
        return ResponseEntity.ok(queryService.getOverview(ticker, period, batchId));
    }

    /** GET /internal/fa/tickers/{ticker}/financials */
    @GetMapping("/{ticker}/financials")
    @Operation(summary = "Get financial metrics history for a ticker")
    public ResponseEntity<TickerFinancialsResponse> getFinancials(
            @PathVariable String ticker,
            @RequestParam(required = false, defaultValue = "QUARTER") String periodType,
            @RequestParam(required = false) Long batchId) {
        return ResponseEntity.ok(queryService.getFinancials(ticker, periodType, batchId));
    }

    /** GET /internal/fa/tickers/{ticker}/ratios */
    @GetMapping("/{ticker}/ratios")
    @Operation(summary = "Get calculated FA ratios for a ticker")
    public ResponseEntity<TickerRatiosResponse> getRatios(
            @PathVariable String ticker,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long batchId) {
        return ResponseEntity.ok(queryService.getRatios(ticker, period, batchId));
    }

    /** GET /internal/fa/tickers/{ticker}/score-history */
    @GetMapping("/{ticker}/score-history")
    @Operation(summary = "Get FA score history across all periods for a ticker")
    public ResponseEntity<TickerScoreHistoryResponse> getScoreHistory(@PathVariable String ticker) {
        return ResponseEntity.ok(queryService.getScoreHistory(ticker));
    }
}
