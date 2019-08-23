// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.vespa.streamingvisitors.tracing.LoggingTraceExporter;
import com.yahoo.vespa.streamingvisitors.tracing.MaxSamplesPerPeriod;
import com.yahoo.vespa.streamingvisitors.tracing.MonotonicNanoClock;
import com.yahoo.vespa.streamingvisitors.tracing.ProbabilisticSampleRate;
import com.yahoo.vespa.streamingvisitors.tracing.SamplingStrategy;
import com.yahoo.vespa.streamingvisitors.tracing.SamplingTraceExporter;
import com.yahoo.vespa.streamingvisitors.tracing.TraceExporter;

import java.util.concurrent.TimeUnit;

public class TracingOptions {

    private final SamplingStrategy samplingStrategy;
    private final TraceExporter traceExporter;
    private final MonotonicNanoClock clock;
    private final int traceLevelOverride;
    private final double traceTimeoutMultiplierThreshold;

    public TracingOptions(SamplingStrategy samplingStrategy, TraceExporter traceExporter,
                          MonotonicNanoClock clock, int traceLevelOverride, double traceTimeoutMultiplierThreshold)
    {
        this.samplingStrategy = samplingStrategy;
        this.traceExporter = traceExporter;
        this.clock = clock;
        this.traceLevelOverride = traceLevelOverride;
        this.traceTimeoutMultiplierThreshold = traceTimeoutMultiplierThreshold;
    }

    public static final TracingOptions DEFAULT;
    public static final int DEFAULT_TRACE_LEVEL_OVERRIDE = 7; // TODO determine appropriate trace level
    // Traces won't be exported unless the query has timed out with a duration that is > timeout * multiplier
    public static final double TRACE_TIMEOUT_MULTIPLIER_THRESHOLD = 5.0;

    static {
        SamplingStrategy queryTraceSampler = ProbabilisticSampleRate.withSystemDefaults(0.5);
        SamplingStrategy logExportSampler  = MaxSamplesPerPeriod.withSteadyClock(TimeUnit.SECONDS.toNanos(10), 1);
        TraceExporter    traceExporter     = new SamplingTraceExporter(new LoggingTraceExporter(), logExportSampler);
        DEFAULT = new TracingOptions(queryTraceSampler, traceExporter, System::nanoTime,
                                     DEFAULT_TRACE_LEVEL_OVERRIDE, TRACE_TIMEOUT_MULTIPLIER_THRESHOLD);
    }

    public SamplingStrategy getSamplingStrategy() {
        return samplingStrategy;
    }

    public TraceExporter getTraceExporter() {
        return traceExporter;
    }

    public MonotonicNanoClock getClock() {
        return clock;
    }

    public int getTraceLevelOverride() {
        return traceLevelOverride;
    }

    public double getTraceTimeoutMultiplierThreshold() {
        return traceTimeoutMultiplierThreshold;
    }

}
