// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

/**
 * Clock which returns a monotonically increasing timestamp from an undefined epoch.
 * The epoch is guaranteed to be stable within a single JVM execution, but not across
 * processes. Should therefore only be used for relative duration tracking, not absolute
 * wall clock time events.
 */
@FunctionalInterface
public interface MonotonicNanoClock {

    long nanoTimeNow();

}
