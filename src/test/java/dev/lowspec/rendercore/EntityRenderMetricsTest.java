package dev.lowspec.rendercore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRenderMetricsTest {
    private RenderCoreConfig previousConfig;

    @BeforeEach
    void enableProfiling() {
        previousConfig = LowSpecRenderCoreClient.CONFIG;
        LowSpecRenderCoreClient.CONFIG = new RenderCoreConfig();
        LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled = true;
    }

    @AfterEach
    void restoreConfig() {
        LowSpecRenderCoreClient.CONFIG = previousConfig;
    }

    @Test
    void recordsEntityExtractionEnqueueAndDispatchSeparately() {
        long extractionStart = System.nanoTime() - 2_000_000L;
        long enqueueStart = System.nanoTime() - 1_000_000L;
        long dispatchStart = System.nanoTime() - 3_000_000L;

        EntityRenderMetrics.endExtraction(extractionStart, true);
        EntityRenderMetrics.endEnqueue(enqueueStart, true);
        EntityRenderMetrics.endDispatch(dispatchStart);
        EntityRenderMetrics.finishFrame();

        EntityRenderMetrics.Snapshot snapshot = EntityRenderMetrics.snapshot();
        assertEquals(1, snapshot.extractionsLastFrame());
        assertEquals(1, snapshot.enqueuesLastFrame());
        assertEquals(1, snapshot.playerExtractionsLastFrame());
        assertEquals(1, snapshot.playerEnqueuesLastFrame());
        assertEquals(1, snapshot.dispatchCallsLastFrame());
        assertTrue(snapshot.averageExtractionMillisPerFrame() >= 1.0);
        assertTrue(snapshot.averageEnqueueMillisPerFrame() >= 0.5);
        assertTrue(snapshot.dispatchMillisLastFrame() >= 2.0);
        assertTrue(snapshot.p95ExtractionMillisPerFrame() >= 1.0);
        assertTrue(snapshot.p95EnqueueMillisPerFrame() >= 0.5);
        assertTrue(snapshot.p95DispatchMillisPerFrame() >= 2.0);
    }

    @Test
    void disabledProfilerDoesNotStartNewMeasurements() {
        LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled = false;
        LowSpecRenderCoreClient.CONFIG.adaptiveEntityVisibilityEnabled = false;

        assertEquals(0L, EntityRenderMetrics.beginExtraction());
        assertEquals(0L, EntityRenderMetrics.beginEnqueue());
        assertEquals(0L, EntityRenderMetrics.beginDispatch());
    }
}
