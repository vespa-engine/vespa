// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.cache.AbstractCache;
import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.HostName;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This encapsulated the curator database of the node repo.
 * It serves reads from an in-memory cache of the content which is invalidated when changed on another node
 * using a global, shared counter. The counter is updated on all write operations, ensured by wrapping write
 * operations in a try block, with the counter increment in a finally block. Locks must be used to ensure consistency.
 *
 * @author bratseth
 * @author jonmv
 */
public class CuratorDatabase {

    private final Curator curator;

    /** A shared atomic counter which is incremented every time we write to the curator database */
    private final CuratorCounter changeGenerationCounter;

    /** A partial cache of the Curator database, which is only valid if generations match */
    private final AtomicReference<Cache> cache = new AtomicReference<>();

    /** Whether we should return data from the cache or always read from ZooKeeper */
    private final boolean useCache;

    private final Object cacheCreationLock = new Object();

    /**
     * Creates a curator database
     *
     * @param curator the curator instance
     * @param root the file system root of the db
     */
    public CuratorDatabase(Curator curator, Path root, boolean useCache) {
        this.useCache = useCache;
        this.curator = curator;
        changeGenerationCounter = new CuratorCounter(curator, root.append("changeCounter"));
        cache.set(newCache(changeGenerationCounter.get()));
    }

    /** Returns all hosts configured to be part of this ZooKeeper cluster */
    public List<HostName> cluster() {
        return Arrays.stream(curator.zooKeeperEnsembleConnectionSpec().split(","))
                .filter(hostAndPort -> !hostAndPort.isEmpty())
                .map(hostAndPort -> hostAndPort.split(":")[0])
                .map(HostName::of)
                .collect(Collectors.toList());
    }

    /** Create a reentrant lock */
    public Lock lock(Path path, Duration timeout) {
        return curator.lock(path, timeout);
    }

    // --------- Write operations ------------------------------------------------------------------------------
    // These must either create a nested transaction ending in a counter increment or not depend on prior state

    /**
     * Creates a new curator transaction against this database and adds it to the given nested transaction.
     * Important: It is the nested transaction which must be committed - never the curator transaction directly.
     */
    public CuratorTransaction newCuratorTransactionIn(NestedTransaction transaction) {
        // Wrap the curator transaction with an increment of the generation counter.
        CountingCuratorTransaction curatorTransaction = new CountingCuratorTransaction(curator, changeGenerationCounter);
        transaction.add(curatorTransaction);
        return curatorTransaction;
    }

    /** Creates a path in curator and all its parents as necessary. If the path already exists this does nothing. */
    void create(Path path) {
        if (curator.create(path))
            changeGenerationCounter.next(); // Increment counter to ensure getChildren sees any change.
    }

    /** Returns whether given path exists */
    boolean exists(Path path) {
        return curator.exists(path);
    }

    // --------- Read operations -------------------------------------------------------------------------------
    // These can read from the memory file system, which accurately mirrors the ZooKeeper content IF
    // the current generation counter is the same as it was when data was put into the cache, AND
    // the data to read is protected by a lock which is held now, and during any writes of the data.

    /** Returns the immediate, local names of the children under this node in any order */
    List<String> getChildren(Path path) { return getSession().getChildren(path); }

    Optional<byte[]> getData(Path path) { return getSession().getData(path); }

    /** Invalidates the current cache if outdated. */
    Session getSession() {
        if (changeGenerationCounter.get() != cache.get().generation)
            synchronized (cacheCreationLock) {
                while (changeGenerationCounter.get() != cache.get().generation)
                    cache.set(newCache(changeGenerationCounter.get()));
            }
            
        return cache.get();
    }

    CacheStats cacheStats() {
        return cache.get().stats();
    }

    /** Caches must only be instantiated using this method */
    private Cache newCache(long generation) {
        return useCache ? new Cache(generation, curator) : new NoCache(generation, curator);
    }

    /**
     * A thread safe partial snapshot of the curator database content with a given generation.
     * This is merely a recording of what Curator returned at various points in time when 
     * it had the counter at this generation.
     */
    private static class Cache implements Session {

        private final long generation;

        /** The curator instance used to fetch missing data */
        protected final Curator curator;

        // The data of this partial state mirror. The amount of curator state mirrored in this may grow
        // over time by multiple threads. Growing is the only operation permitted by this.
        // The content of the map is immutable.
        private final Map<Path, List<String>> children = new ConcurrentHashMap<>();
        private final Map<Path, Optional<byte[]>> data = new ConcurrentHashMap<>();

        private final AbstractCache.SimpleStatsCounter stats = new AbstractCache.SimpleStatsCounter();

        /** Create an empty snapshot at a given generation (as an empty snapshot is a valid partial snapshot) */
        private Cache(long generation, Curator curator) {
            this.generation = generation;
            this.curator = curator;
        }

        @Override
        public List<String> getChildren(Path path) {
            return get(children, path, () -> ImmutableList.copyOf(curator.getChildren(path)));
        }

        @Override
        public Optional<byte[]> getData(Path path) {
            return get(data, path, () -> curator.getData(path)).map(data -> Arrays.copyOf(data, data.length));
        }

        private <T> T get(Map<Path, T> values, Path path, Supplier<T> loader) {
            return values.compute(path, (key, value) -> {
                if (value == null) {
                    stats.recordMisses(1);
                    return loader.get();
                }
                stats.recordHits(1);
                return value;
            });
        }

        public CacheStats stats() {
            var stats = this.stats.snapshot();
            return new CacheStats(stats.hitRate(), stats.evictionCount(), children.size() + data.size());
        }

    }

    /** An implementation of the curator database cache which does no caching */
    private static class NoCache extends Cache {

        private NoCache(long generation, Curator curator) { super(generation, curator); }

        @Override
        public List<String> getChildren(Path path) { return curator.getChildren(path); }

        @Override
        public Optional<byte[]> getData(Path path) { return curator.getData(path); }

    }

    interface Session {

        /**
         * Returns the children of this path, which may be empty.
         */
        List<String> getChildren(Path path);

        /**
         * Returns the a copy of the content of this child - which may be empty.
         */
        Optional<byte[]> getData(Path path);

    }

}
