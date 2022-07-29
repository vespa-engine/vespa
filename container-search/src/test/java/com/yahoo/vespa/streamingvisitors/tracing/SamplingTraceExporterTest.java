// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SamplingTraceExporterTest {

    @Test
    void sampling_decision_is_deferred_to_provided_sampler() {
        var exporter = mock(TraceExporter.class);
        var sampler = mock(SamplingStrategy.class);
        when(sampler.shouldSample()).thenReturn(true, false);
        var samplingExporter = new SamplingTraceExporter(exporter, sampler);

        samplingExporter.maybeExport(() -> new TraceDescription(null, ""));
        verify(exporter, times(1)).maybeExport(any());

        samplingExporter.maybeExport(() -> new TraceDescription(null, ""));
        verify(exporter, times(1)).maybeExport(any()); // No further invocations since last
    }

}
