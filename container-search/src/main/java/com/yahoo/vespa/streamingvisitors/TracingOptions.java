// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.vespa.streamingvisitors.tracing.LoggingTraceExporter;
import com.yahoo.vespa.streamingvisitors.tracing.MaxSamplesPerPeriod;
import com.yahoo.vespa.streamingvisitors.tracing.MonotonicNanoClock;
import com.yahoo.vespa.streamingvisitors.tracing.ProbabilisticSampleRate;
import com.yahoo.vespa.streamingvisitors.tracing.SamplingStrategy;
import com.yahoo.vespa.streamingvisitors.tracing.SamplingTraceExporter;
import com.yahoo.vespa.streamingvisitors.tracing.TraceExporter;

import java.util.concurrent.TimeUnit;

/**
 * Encapsulates all trace-related components and options used by the streaming search Searcher.
 *
 * Provides a DEFAULT static instance which has the following characteristics:
 *   - Approximately 1 query every second is traced
 *   - Trace level is set to 7 for traced queries
 *   - Only emits traces for queries that have timed out and where the elapsed time is at least 2x
 *     of the timeout specified in the query itself
 *   - Emits traces to the Vespa log
 *   - Only 2 traces every 10 seconds may be emitted to the log
 */
public class TracingOptions {

    private final SamplingStrategy samplingStrategy;
    private final TraceExporter traceExporter;
    private final MonotonicNanoClock clock;
    private final int traceLevelOverride;
    private final double traceTimeoutMultiplierThreshold;

    /**
     * @param samplingStrategy used for choosing if a query should have its trace level implicitly altered.
     * @param traceExporter used for emitting a visitor session trace to someplace it may be debugged later.
     * @param clock monotonic clock used for relative time tracking.
     * @param traceLevelOverride if a query is trace-sampled, its traceLevel will be set to this value
     * @param traceTimeoutMultiplierThreshold only export traces if the elapsed time is greater than the query timeout * this value
     */
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
    public static final double TRACE_TIMEOUT_MULTIPLIER_THRESHOLD = 2.0;

    static {
        SamplingStrategy queryTraceSampler = ProbabilisticSampleRate.withSystemDefaults(1);
        SamplingStrategy logExportSampler  = MaxSamplesPerPeriod.withSteadyClock(TimeUnit.SECONDS.toNanos(10), 2);
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
