// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import com.yahoo.fs4.Packet;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.search.Query;
import com.yahoo.processing.request.CompoundName;


/**
 * The cache control logic for FastSearcher
 *
 * @author Steinar Knutsen
 */
public class CacheControl {

    private static final CompoundName nocachewrite=new CompoundName("nocachewrite");

    /** Whether this CacheControl actually should cache hits at all. */
    private final boolean activeCache;

    /** Direct unsychronized cache access */
    private final PacketCache packetCache;

    public CacheControl(int sizeMegaBytes, double cacheTimeOutSeconds) {
        activeCache = sizeMegaBytes > 0 && cacheTimeOutSeconds > 0.0d;
        if (activeCache) {
            packetCache = new PacketCache(sizeMegaBytes, 0, cacheTimeOutSeconds);
        } else {
            packetCache = null;
        }
    }

    /** Returns the capacity of the packet cache in megabytes */
    public final int capacity() {
        return packetCache.getCapacity();
    }

    public final boolean useCache(Query query) {
        return (activeCache && !query.getNoCache());
    }

    public final PacketWrapper lookup(CacheKey key, Query query) {
        if ((key != null) && useCache(query)) {
            long now = System.currentTimeMillis();
            synchronized (packetCache) {
                return packetCache.get(key, now);
            }
        }
        return null;
    }

    // updates first phase in multi phase search
    void updateCacheEntry(CacheKey key, Query query, QueryResultPacket resultPacket) {
        long oldTimestamp;
        if (!activeCache) return;

        PacketWrapper wrapper = lookup(key, query);
        if (wrapper == null) return;

        // The timestamp is owned by the QueryResultPacket, this is why this
        // update method puts entries into the cache differently from elsewhere
        oldTimestamp = wrapper.getTimestamp();
        wrapper = (PacketWrapper) wrapper.clone();
        wrapper.addResultPacket(resultPacket);
        synchronized (packetCache) {
            packetCache.put(key, wrapper, oldTimestamp);
        }
    }

    // updates phases after first phase phase in multi phase search
    void updateCacheEntry(CacheKey key, Query query, DocsumPacketKey[] packetKeys, Packet[] packets) {
        if (!activeCache) return;

        PacketWrapper wrapper = lookup(key, query);
        if (wrapper== null) return;

        wrapper = (PacketWrapper) wrapper.clone();
        wrapper.addDocsums(packetKeys, packets);
        synchronized (packetCache) {
            packetCache.put(key, wrapper, wrapper.getTimestamp());
        }
    }

    void cache(CacheKey key, Query query, DocsumPacketKey[] packetKeys, Packet[] packets) {
        if ( ! activeCache) return;

        if (query.getNoCache()) return;
        if (query.properties().getBoolean(nocachewrite)) return;

        PacketWrapper wrapper = lookup(key, query);
        if (wrapper == null) {
            wrapper = new PacketWrapper(key, packetKeys,packets);
            long now = System.currentTimeMillis();
            synchronized (packetCache) {
                packetCache.put(key, wrapper, now);
            }
        } else {
            wrapper = (PacketWrapper) wrapper.clone();
            wrapper.addResultPacket((QueryResultPacket) packets[0]);
            wrapper.addDocsums(packetKeys, packets, 1);
            synchronized (packetCache) {
                packetCache.put(key, wrapper, wrapper.getTimestamp());
            }
        }
    }

    /** Test method. */
    public void clear() {
        if (packetCache != null) {
            packetCache.clear();
        }
    }

}
