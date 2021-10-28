// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

/**
 * A sampling strategy makes the high-level decision of whether or not a query
 * should be traced.
 *
 * Callers should be able to expect that calling shouldSample() is a cheap operation
 * with little or no underlying locking. This in turn means that the sampling strategy
 * may be consulted for each query with minimal overhead.
 */
public interface SamplingStrategy {

    boolean shouldSample();

}
