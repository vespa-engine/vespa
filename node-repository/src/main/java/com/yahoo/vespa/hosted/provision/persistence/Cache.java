// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.yahoo.path.Path;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * A simple wrapper around a {@link com.google.common.cache.Cache} that invalidates cache entries based on a version
 * number, e.g. ZNode version or cversion.
 *
 * @author mpolden
 */
public class Cache<T> {

    private final com.google.common.cache.Cache<Path, Entry<T>> cache;

    public Cache(long size) {
        this.cache = CacheBuilder.newBuilder().maximumSize(size).recordStats().build();
    }

    /** Returns statistics for this */
    public Stats stats() {
        var stats = cache.stats();
        return new Stats(stats.hitRate(), stats.evictionCount(), cache.size());
    }

    /**
     * Returns the value associated with given path in this cache. The cached value is returned if its version matches
     * currentVersion, otherwise a new value is obtained from loader.
     *
     * The value of currentVersion must always be read from ZooKeeper.
     */
    public Optional<T> get(Path path, int currentVersion, Callable<Entry<T>> loader) {
        try {
            Entry<T> entry = cache.get(path, loader);
            if (entry.version != currentVersion) {
                cache.invalidate(path);
                entry = cache.get(path, loader);
            }
            return Optional.of(entry.value);
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
    }

    /** A versioned entry of this cache */
    public static class Entry<T> {

        private final T value;
        private final int version;

        public Entry(T value, int version) {
            this.value = Objects.requireNonNull(value);
            this.version = version;
        }

        @Override
        public String toString() {
            return value + " version " + version;
        }

    }

    /** Basic cache statistics */
    public static class Stats {

        private final double hitRate;
        private final long evictionCount;
        private final long size;

        public Stats(double hitRate, long evictionCount, long size) {
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

}
