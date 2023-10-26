// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

/**
 * Statistics for caches used by {@link com.yahoo.vespa.hosted.provision.NodeRepository}.
 *
 * @author mpolden
 */
public class CacheStats {

    private final double hitRate;
    private final long evictionCount;
    private final long size;

    public CacheStats(double hitRate, long evictionCount, long size) {
        this.hitRate = hitRate;
        this.evictionCount = evictionCount;
        this.size = size;
    }

    /** The fraction of lookups that resulted in a hit */
    public double hitRate() {
        return hitRate;
    }

    /** The number of entries that have been evicted */
    public long evictionCount() {
        return evictionCount;
    }

    /** The current size of the cache */
    public long size() {
        return size;
    }

}
