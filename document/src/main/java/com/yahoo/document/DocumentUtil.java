// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * Class containing static utility function related to documents.
 * @author Einar M Rosenvinge
 * @since 5.1.9
 */
@Deprecated(forRemoval = true)
public class DocumentUtil {
    /**
     * A convenience method that can be used to calculate a max pending queue size given
     * the number of threads processing the documents, their size, and the memory available.
     *
     * @return the max pending size (in bytes) that should be used.
     */
    public static int calculateMaxPendingSize(double maxConcurrentFactor, double documentExpansionFactor, int containerCoreMemoryMb) {
        final long heapBytes = Runtime.getRuntime().maxMemory();
        final long heapMb = heapBytes / 1024L / 1024L;
        final double maxPendingMb = ((double) (heapMb - containerCoreMemoryMb)) / (1.0d + (maxConcurrentFactor * documentExpansionFactor));
        long maxPendingBytes = ((long) (maxPendingMb * 1024.0d)) * 1024L;
        if (maxPendingBytes < (1024L * 1024L)) {
            maxPendingBytes = 1024L * 1024L;  //1 MB
        }
        if (maxPendingBytes > (heapBytes / 5L)) {
            maxPendingBytes = heapBytes / 5L;  //we do not want a maxPendingBytes greater than 1/5 heap (we probably have a very low expansion factor)
        }
        if (maxPendingBytes > (1<<30)) {  //we don't want a maxPendingBytes greater than 1G
            maxPendingBytes = 1<<30;
        }
        return (int) maxPendingBytes;
    }
}
