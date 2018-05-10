// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.yahoo.log.LogLevel;


/**
 * An LRU cache using number of hits cached inside the results as
 * size limiting factor. Directly modelled after com.yahoo.collections.Cache.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
// TODO: Remove packet cache as it timed out a long time ago.
// 1 - It does not work with grouping, 2 the packet protocol is eroding away.
public class PacketCache extends LinkedHashMap<CacheKey, PacketWrapper> {

    private static final long serialVersionUID = -7403077211906108356L;

    /** The <i>current</i> number of bytes of packets in this cache */
    private int totalSize;

    /** The maximum number of bytes of packets in this cache */
    private final int capacity;

    /** The max size of a cached item compared to the total size */
    private int maxCacheItemPercentage = 1;

    /** The max age for a valid cache entry, 0 mean infinite */
    private final long maxAge;

    private static final Logger log = Logger.getLogger(PacketCache.class.getName());

    public void clear() {
        super.clear();
        totalSize = 0;
    }

    /**
     * Sets the max size of a cached item compared to the total size
     * Cache requests for larger objects will be ignored
     */
    public void setMaxCacheItemPercentage(int maxCapacityPercentage) {
        maxCacheItemPercentage = maxCapacityPercentage;
    }

    /**
     * Creates a cache with a size given by
     * cachesizemegabytes*2^20+cachesizebytes
     *
     * @param capacityMegaBytes the cache size, measured in megabytes
     * @param capacityBytes additional number of bytes to add to the cache size
     * @param maxAge seconds a cache entry is valid, 0 or less are illegal arguments
     */
    public PacketCache(int capacityMegaBytes,int capacityBytes,double maxAge) {
        // hardcoded inital entry capacity, won't matter much anyway
        // after a while
        super(12500, 1.0f, true);
        if (maxAge <= 0.0d) {
            throw new IllegalArgumentException("maxAge <= 0 not legal on 5.1, use some very large number for no timeout.");
        }
        if (capacityMegaBytes > (Integer.MAX_VALUE >> 20)) {
            log.log(LogLevel.INFO, "Packet cache of more than 2 GB requested. Reverting to 2 GB packet cache.");
            this.capacity = Integer.MAX_VALUE;
        } else {
            this.capacity = (capacityMegaBytes << 20) + capacityBytes;
        }
        if (this.capacity <= 0) {
            throw new IllegalArgumentException("Total cache size set to 0 or less bytes. If no caching is desired, avoid creating this object instead.");
        }
        this.maxAge = (long) (maxAge * 1000.0d);
    }

    /**
     * Overrides LinkedHashMap.removeEldestEntry as suggested to implement LRU cache.
     */
    protected boolean removeEldestEntry(Map.Entry<CacheKey, PacketWrapper> eldest)
    {
        if (totalSize > capacity) {
            totalSize -= eldest.getValue().getPacketsSize();
            return true;
        }
        return false;
    }

    private void removeOverflow() {
        if (totalSize < capacity) return;

        for (Iterator<PacketWrapper> i = values().iterator(); i.hasNext();) {
            PacketWrapper eldestEntry = i.next();
            totalSize -= eldestEntry.getPacketsSize();

            i.remove();
            if (totalSize < capacity) {
                break;
            }
        }
    }

    public int getCapacity() {
        return capacity >> 20;
    }

    public int getByteCapacity() {
        return capacity;
    }

    /**
     * Adds a PacketWrapper object to this cache,
     * unless the size is more than maxCacheItemPercentage of the total size
     */
    public PacketWrapper put(CacheKey key, PacketWrapper value) {
        return put(key, value, System.currentTimeMillis());
    }

    /**
     * Adds a BasicPacket array to this cache,
     * unless the size is more than maxCacheItemPercentage of the total size
     *
     * @param timestamp the timestamp for the first packet in the array,
     * unit milliseconds
     */
    public PacketWrapper put(CacheKey key, PacketWrapper result, long timestamp) {
        int size = result.getPacketsSize();

        if (size > 0) {
            result.setTimestamp(timestamp);
        }

        // don't insert if it is too big
        if (size * 100 > capacity * maxCacheItemPercentage) {
            // removeField the old one since that is now stale.
            return remove(key);
        }

        totalSize += size;
        PacketWrapper previous = super.put(key, result);
        if (previous != null) {
            totalSize -= previous.getPacketsSize();
        }
        if (totalSize > (capacity * 1.1)) {
            removeOverflow();
        }

        return previous;
    }

    public PacketWrapper get(CacheKey key) {
        return get(key, System.currentTimeMillis());
    }

    public PacketWrapper get(CacheKey key, long now) {
        PacketWrapper result = super.get(key);

        if (result == null) {
            return result;
        }

        long timestamp = result.getTimestamp();

        if ((now - timestamp) > maxAge) {
            remove(key);
            return null;
        } else {
            return result;
        }
    }

    public PacketWrapper remove(CacheKey key) {
        PacketWrapper removed = super.remove(key);

        if (removed != null) {
            totalSize -= removed.getPacketsSize();
        }
        return removed;
    }

    public int totalPacketSize() {
        return totalSize;
    }

}
