// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import java.util.function.Supplier;

/**
 * Potentially exports a trace to an underlying consumer. "Potentially" here means
 * that the exporter may itself sample or otherwise limit which queries are actually
 * exported.
 */
public interface TraceExporter {

    void maybeExport(Supplier<TraceDescription> traceDescriptionSupplier);

}
