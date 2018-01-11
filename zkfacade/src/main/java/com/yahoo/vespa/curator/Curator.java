// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Curator interface for Vespa.
 * This contains method for constructing common recipes and utilities as well as
 * a small wrapper API for common operations which uses typed paths and avoids throwing checked exceptions.
 * <p>
 * There is a mock implementation in MockCurator.
 *
 * @author vegardh
 * @author bratseth
 */
public class Curator implements AutoCloseable {

    private static final long UNKNOWN_HOST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final int ZK_SESSION_TIMEOUT = 30000;
    private static final int ZK_CONNECTION_TIMEOUT = 30000;

    private static final int BASE_SLEEP_TIME = 1000; //ms
    private static final int MAX_RETRIES = 10;

    protected final RetryPolicy retryPolicy;

    private final CuratorFramework curatorFramework;
    private final String connectionSpec; // May be a subset of the servers in the ensemble

    private final String zooKeeperEnsembleConnectionSpec;
    private final int zooKeeperEnsembleCount;

    /** Creates a curator instance from a comma-separated string of ZooKeeper host:port strings */
    public static Curator create(String connectionSpec) {
        return new Curator(connectionSpec, connectionSpec);
    }

    // Depend on ZooKeeperServer to make sure it is started first
    // TODO: Move zookeeperserver config out of configserverconfig (requires update of controller services.xml as well)
    @Inject
    public Curator(ConfigserverConfig configserverConfig, ZooKeeperServer server) {
        this(configserverConfig, createConnectionSpec(configserverConfig));
    }

    private Curator(ConfigserverConfig configserverConfig, String zooKeeperEnsembleConnectionSpec) {
        this(configserverConfig.zookeeperLocalhostAffinity() ?
                HostName.getLocalhost() : zooKeeperEnsembleConnectionSpec,
                zooKeeperEnsembleConnectionSpec);
    }

    private Curator(String connectionSpec, String zooKeeperEnsembleConnectionSpec) {
        this(connectionSpec,
                zooKeeperEnsembleConnectionSpec,
                (retryPolicy) -> CuratorFrameworkFactory
                        .builder()
                        .retryPolicy(retryPolicy)
                        .sessionTimeoutMs(ZK_SESSION_TIMEOUT)
                        .connectionTimeoutMs(ZK_CONNECTION_TIMEOUT)
                        .connectString(connectionSpec)
                        .zookeeperFactory(new DNSResolvingFixerZooKeeperFactory(UNKNOWN_HOST_TIMEOUT_MILLIS))
                        .build());
    }

    protected Curator(String connectionSpec,
                      String zooKeeperEnsembleConnectionSpec,
                      Function<RetryPolicy, CuratorFramework> curatorFactory) {
        this(connectionSpec, zooKeeperEnsembleConnectionSpec, curatorFactory,
                new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES));
    }

    private Curator(String connectionSpec,
                    String zooKeeperEnsembleConnectionSpec,
                    Function<RetryPolicy, CuratorFramework> curatorFactory,
                    RetryPolicy retryPolicy) {
        this.connectionSpec = connectionSpec;
        this.retryPolicy = retryPolicy;
        this.curatorFramework = curatorFactory.apply(retryPolicy);
        if (this.curatorFramework != null) {
            validateConnectionSpec(connectionSpec);
            validateConnectionSpec(zooKeeperEnsembleConnectionSpec);
            addFakeListener();
            curatorFramework.start();
        }

        this.zooKeeperEnsembleConnectionSpec = zooKeeperEnsembleConnectionSpec;
        this.zooKeeperEnsembleCount = zooKeeperEnsembleConnectionSpec.split(",").length;
    }

    static String createConnectionSpec(ConfigserverConfig config) {
        StringBuilder connectionSpec = new StringBuilder();
        for (int i = 0; i < config.zookeeperserver().size(); i++) {
            if (connectionSpec.length() > 0) {
                connectionSpec.append(',');
            }
            ConfigserverConfig.Zookeeperserver server = config.zookeeperserver(i);
            connectionSpec.append(server.hostname());
            connectionSpec.append(':');
            connectionSpec.append(server.port());
        }
        return connectionSpec.toString();
    }

    private static void validateConnectionSpec(String connectionSpec) {
        if (connectionSpec == null || connectionSpec.isEmpty())
            throw new IllegalArgumentException(String.format("Connections spec '%s' is not valid", connectionSpec));
    }

    /**
     * Returns the ZooKeeper "connect string" used by curator: a comma-separated list of
     * host:port of ZooKeeper endpoints to connect to. This may be a subset of
     * zooKeeperEnsembleConnectionSpec() if there's some affinity, e.g. for
     * performance reasons.
     *
     * This may be empty but never null 
     */
    public String connectionSpec() { return connectionSpec; }

    /** For internal use; prefer creating a {@link CuratorCounter} */
    public DistributedAtomicLong createAtomicCounter(String path) {
        return new DistributedAtomicLong(curatorFramework, path, new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES));
    }

    /** For internal use; prefer creating a {@link com.yahoo.vespa.curator.recipes.CuratorLock} */
    public InterProcessLock createMutex(String lockPath) {
        return new InterProcessMutex(curatorFramework, lockPath);
    }

    // To avoid getting warning in log, see ticket 6389740
    private void addFakeListener() {
        curatorFramework.getConnectionStateListenable().addListener(new ConnectionStateListener() {
            @Override
            public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
                // empty, not needed now
            }
        });
    }

    public CompletionWaiter getCompletionWaiter(Path waiterPath, int numMembers, String id) {
        return CuratorCompletionWaiter.create(curatorFramework, waiterPath, numMembers, id);
    }

    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, int numMembers, String id) {
        return CuratorCompletionWaiter.createAndInitialize(this, parentPath, waiterNode, numMembers, id);
    }

    /** Creates a listenable cache which keeps in sync with changes to all the immediate children of a path */
    public DirectoryCache createDirectoryCache(String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        return new PathChildrenCacheWrapper(framework(), path, cacheData, dataIsCompressed, executorService);
    }

    /** Creates a listenable cache which keeps in sync with changes to a given node */
    public FileCache createFileCache(String path, boolean dataIsCompressed) {
        return new NodeCacheWrapper(framework(), path, dataIsCompressed);
    }

    /** A convenience method which returns whether the given path exists */
    public boolean exists(Path path) {
        try {
            return framework().checkExists().forPath(path.getAbsolute()) != null;
        }
        catch (Exception e) {
            throw new RuntimeException("Could not check existence of " + path.getAbsolute(), e);
        }
    }

    /**
     * A convenience method which sets some content at a path.
     * If the path and any of its parents does not exists they are created.
     */
    public void set(Path path, byte[] data) {
        String absolutePath = path.getAbsolute();
        try {
            if ( ! exists(path))
                framework().create().creatingParentsIfNeeded().forPath(absolutePath, data);
            else
                framework().setData().forPath(absolutePath, data);
        } catch (Exception e) {
            throw new RuntimeException("Could not set data at " + absolutePath, e);
        }
    }

    /**
     * Creates an empty node at a path, creating any parents as necessary.
     * If the node already exists nothing is done.
     */
    public void create(Path path) {
        if (exists(path)) return;

        String absolutePath = path.getAbsolute();
        try {
            framework().create().creatingParentsIfNeeded().forPath(absolutePath, new byte[0]);
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            // Path created between exists() and create() call, do nothing
        } catch (Exception e) {
            throw new RuntimeException("Could not create " + absolutePath, e);
        }
    }

    /**
     * Creates all the given paths in a single transaction. Any paths which already exists are ignored.
     */
    public void createAtomically(Path... paths) {
        try {
            CuratorTransaction transaction = framework().inTransaction();
            for (Path path : paths) {
                if ( ! exists(path)) {
                    transaction = transaction.create().forPath(path.getAbsolute(), new byte[0]).and();
                }
            }
            ((CuratorTransactionFinal)transaction).commit();
        } catch (Exception e) {
            throw new RuntimeException("Could not create " + Arrays.toString(paths), e);
        }
    }

    /**
     * Deletes the given path and any children it may have.
     * If the path does not exists nothing is done.
     */
    public void delete(Path path) {
        if ( ! exists(path)) return;

        try {
            framework().delete().guaranteed().deletingChildrenIfNeeded().forPath(path.getAbsolute());
        } catch (Exception e) {
            throw new RuntimeException("Could not delete " + path.getAbsolute(), e);
        }
    }

    /**
     * Returns the names of the children at the given path.
     * If the path does not exist or have no children an empty list (never null) is returned.
     */
    public List<String> getChildren(Path path) {
        if ( ! exists(path)) return Collections.emptyList();

        try {
            return framework().getChildren().forPath(path.getAbsolute());
        } catch (Exception e) {
            throw new RuntimeException("Could not get children of " + path.getAbsolute(), e);
        }
    }

    /**
     * Returns the data at the given path, which may be a zero-length buffer if the node exists but have no data.
     * Empty is returned if the path does not exist.
     */
    public Optional<byte[]> getData(Path path) {
        if ( ! exists(path)) return Optional.empty();

        try {
            return Optional.of(framework().getData().forPath(path.getAbsolute()));
        }
        catch (Exception e) {
            throw new RuntimeException("Could not get data at " + path.getAbsolute(), e);
        }
    }

    /** Returns the curator framework API */
    public CuratorFramework framework() {
        return curatorFramework;
    }

    @Override
    public void close() {
        curatorFramework.close();
    }

    /**
     * Interface for waiting for completion of an operation
     */
    public interface CompletionWaiter {

        /**
         * Awaits completion of something. Blocks until an implementation defined
         * condition has been met.
         *
         * @param timeout timeout for blocking await call.
         * @throws CompletionTimeoutException if timeout is reached without completion.
         */
        void awaitCompletion(Duration timeout);

        /**
         * Notify completion of something. This method does not block and is called by clients
         * that want to notify the completion waiter that something has completed.
         */
        void notifyCompletion();

    }

    /**
     * A listenable cache of all the immediate children of a curator path.
     * This wraps the Curator PathChildrenCache recipe to allow us to mock it.
     */
    public interface DirectoryCache {

        void start();

        void addListener(PathChildrenCacheListener listener);

        List<ChildData> getCurrentData();

        void close();

    }

    /**
     * A listenable cache of the content of a single curator path.
     * This wraps the Curator NodeCache recipe to allow us to mock it.
     */
    public interface FileCache {

        void start();

        void addListener(NodeCacheListener listener);

        ChildData getCurrentData();

        void close();

    }

    /**
     * @return The non-null connect string containing all ZooKeeper servers in the ensemble.
     * WARNING: This may be different from the servers this Curator may connect to.
     * TODO: Move method out of this class.
     */
    public String zooKeeperEnsembleConnectionSpec() {
        return zooKeeperEnsembleConnectionSpec;
    }

    /**
     * Returns the number of zooKeeper servers in this ensemble.
     * WARNING: This may be different from the number of servers this Curator may connect to.
     * TODO: Move method out of this class.
     */
    public int zooKeeperEnsembleCount() { return zooKeeperEnsembleCount; }
}
