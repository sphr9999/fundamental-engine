package com.eyelanding.fundamentalengine.api.controller;

import com.eyelanding.fundamentalengine.api.dto.EnrichmentProgressResponse;
import com.eyelanding.fundamentalengine.api.dto.EnrichmentTriggerRequest;
import com.eyelanding.fundamentalengine.api.dto.ImportBatchResponse;
import com.eyelanding.fundamentalengine.application.enrichment.EnrichmentOrchestrator;
import com.eyelanding.fundamentalengine.application.enrichment.EnrichmentRequest;
import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaImportBatchEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for VCI API data enrichment operations.
 *
 * <p>The trigger endpoint runs enrichment asynchronously in a background thread.
 * It returns immediately with a batch ID that can be polled for progress.</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/fa/enrichment")
@RequiredArgsConstructor
public class FaEnrichmentController {

    private final EnrichmentOrchestrator enrichmentOrchestrator;
    private final FaImportBatchRepository batchRepository;

    /** Single-thread executor so only one enrichment runs at a time. */
    private final ExecutorService enrichmentExecutor = Executors.newSingleThreadExecutor();

    /** Track running enrichment to prevent duplicate triggers. */
    private final Map<String, Long> runningEnrichments = new ConcurrentHashMap<>();

    /**
     * Triggers data enrichment from the VCI API asynchronously.
     *
     * <p>Creates a batch immediately and returns the batch ID. The enrichment
     * runs in a background thread. Poll {@code GET /batches/{batchId}} for progress.</p>
     *
     * @param request the enrichment trigger parameters
     * @return batch ID and initial status (PROCESSING)
     */
    @PostMapping("/trigger")
    public ResponseEntity<?> triggerEnrichment(
            @RequestBody EnrichmentTriggerRequest request) {

        // Prevent duplicate triggers
        if (!runningEnrichments.isEmpty()) {
            Long runningBatchId = runningEnrichments.values().iterator().next();
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Enrichment already running",
                    "runningBatchId", runningBatchId,
                    "message", "Use GET /internal/fa/enrichment/batches/" + runningBatchId + " to check progress"
            ));
        }

        log.info("Enrichment triggered: tickers={}, exchanges={}, reportTypes={}",
                request.tickers(), request.exchanges(), request.reportTypes());

        var enrichRequest = new EnrichmentRequest(
                request.tickers(),
                request.exchanges(),
                request.reportTypes(),
                request.period(),
                request.importedBy()
        );

        // Create batch immediately so we can return batchId
        Long batchId = enrichmentOrchestrator.createBatch(enrichRequest);
        runningEnrichments.put("current", batchId);

        // Run enrichment in background
        enrichmentExecutor.submit(() -> {
            try {
                enrichmentOrchestrator.enrichAll(batchId, enrichRequest);
            } catch (Exception e) {
                log.error("Enrichment batch {} failed unexpectedly: {}", batchId, e.getMessage(), e);
            } finally {
                runningEnrichments.remove("current");
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "batchId", batchId,
                "status", "PROCESSING",
                "message", "Enrichment started in background. Poll GET /internal/fa/enrichment/batches/" + batchId + " for progress."
        ));
    }

    /**
     * Gets the status of an enrichment batch.
     *
     * @param batchId the batch ID to look up
     * @return the batch details, or 404 if not found
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ImportBatchResponse> getBatchStatus(@PathVariable Long batchId) {
        return batchRepository.findById(batchId)
                .map(batch -> ResponseEntity.ok(toResponse(batch)))
                .orElse(ResponseEntity.notFound().build());
    }

    private ImportBatchResponse toResponse(FaImportBatchEntity batch) {
        return ImportBatchResponse.builder()
                .batchId(batch.getId())
                .status(batch.getStatus())
                .sourceFileName(batch.getSourceFileName())
                .sourceFileChecksum(batch.getSourceFileChecksum())
                .reportPeriod(batch.getReportPeriod())
                .totalRows(batch.getTotalRows())
                .successRows(batch.getSuccessRows())
                .warningRows(batch.getWarningRows())
                .errorRows(batch.getErrorRows())
                .importedBy(batch.getImportedBy())
                .startedAt(batch.getStartedAt())
                .finishedAt(batch.getFinishedAt())
                .errorMessage(batch.getErrorMessage())
                .build();
    }
}
