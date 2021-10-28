// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import java.util.function.Supplier;

/**
 * Trace exporter which only exports a subset of traces as decided by the provided sampling strategy.
 */
public class SamplingTraceExporter implements TraceExporter {

    private final TraceExporter wrappedExporter;
    private final SamplingStrategy samplingStrategy;

    public SamplingTraceExporter(TraceExporter wrappedExporter, SamplingStrategy samplingStrategy) {
        this.wrappedExporter = wrappedExporter;
        this.samplingStrategy = samplingStrategy;
    }

    @Override
    public void maybeExport(Supplier<TraceDescription> traceDescriptionSupplier) {
        if (samplingStrategy.shouldSample()) {
            wrappedExporter.maybeExport(traceDescriptionSupplier);
        }
    }

}
