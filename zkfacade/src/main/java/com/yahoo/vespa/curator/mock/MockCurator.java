// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.mock;

import com.yahoo.component.annotation.Inject;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * <p>A <b>non thread safe</b> mock of the curator API.
 * The methods are implemented lazily, due to laziness.
 * You may trigger an UnsupportedOperationException, and in some cases a NullPointerException in using
 * this, which means additional functionality is needed.
 * Due to the "fluent API" style of Curator managing to break JavaDoc at a fundamental level, there is no
 * documentation on the contract of each method. The behavior here is deduced by observing what using code exists
 * and peeking at the Curator code. It may be incorrect in some corner cases.</p>
 *
 * <p>Contains some code from PathUtils in ZooKeeper, licensed under the Apache 2.0 license.</p>
 *
 * @author bratseth
 */
public class MockCurator extends Curator {

    private String zooKeeperEnsembleConnectionSpec = "";

    /** Creates a mock curator with stable ordering */
    @Inject
    public MockCurator() {
        this(true);
    }

    /**
     * Creates a mock curator
     *
     * @param stableOrdering if true children of a node are returned in the same order each time they are queried.
     *                       This is not what ZooKeeper does.
     */
    public MockCurator(boolean stableOrdering) {
        super("host1:2181", "host1:2181", (retryPolicy) -> new MockCuratorFramework(stableOrdering, false));
    }

    private MockCuratorFramework mockFramework() {
        return (MockCuratorFramework) super.framework();
    }

    /**
     * Lists the entire content of this curator instance as a multiline string.
     * Useful for debugging.
     */
    public String dumpState() { return mockFramework().fileSystem().dumpState(); }

    /** Returns an atomic counter in this, or empty if no such counter is created */
    public Optional<DistributedAtomicLong> counter(String path) {
        return Optional.ofNullable(mockFramework().atomicCounters().get(path));
    }

    /**
     * Sets the ZooKeeper ensemble connection spec, which must be on the form
     * host1:port,host2:port ...
     */
    public void setZooKeeperEnsembleConnectionSpec(String ensembleSpec) {
        this.zooKeeperEnsembleConnectionSpec = ensembleSpec;
    }

    @Override
    public String zooKeeperEnsembleConnectionSpec() {
        return zooKeeperEnsembleConnectionSpec;
    }

    @Override
    public DistributedAtomicLong createAtomicCounter(String path) {
        return mockFramework().createAtomicCounter(path);
    }

    @Override
    public InterProcessLock createMutex(String path) {
        return mockFramework().createMutex(path);
    }

    @Override
    public CompletionWaiter getCompletionWaiter(Path parentPath, int numMembers, String id) {
        return mockFramework().createCompletionWaiter();
    }

    @Override
    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, int numMembers, String id) {
        return mockFramework().createCompletionWaiter();
    }

    @Override
    public DirectoryCache createDirectoryCache(String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        return mockFramework().createDirectoryCache(path);
    }

    @Override
    public FileCache createFileCache(String path, boolean dataIsCompressed) {
        return mockFramework().createFileCache(path);
    }

    @Override
    public int zooKeeperEnsembleCount() { return 1; }

}
