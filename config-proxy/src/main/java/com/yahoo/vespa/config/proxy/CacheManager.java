// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.RawConfig;

/**
 * Manages memory and disk caches.
 *
 * @author musum
 */
public class CacheManager {

    private final MemoryCache memoryCache;

    public CacheManager(MemoryCache memoryCache) {
        this.memoryCache = memoryCache;
    }

    static CacheManager createTestCacheManager() {
        return CacheManager.createTestCacheManager(new MemoryCache());
    }

    static CacheManager createTestCacheManager(MemoryCache memoryCache) {
        return new CacheManager(memoryCache);
    }

    void putInCache(RawConfig config) {
        putInMemoryCache(config);
    }

    private void putInMemoryCache(RawConfig config) {
        if (!config.isError()) {
            memoryCache.put(config);
        }
    }

    public MemoryCache getMemoryCache() {
        return memoryCache;
    }

    // Only for testing.
    int getCacheSize() {
        return getMemoryCache().size();
    }

}
