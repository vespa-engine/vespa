// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.api.VespaCurator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
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
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Logger;

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
public class Curator implements VespaCurator, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Curator.class.getName());
    private static final File ZK_CLIENT_CONFIG_FILE = new File(Defaults.getDefaults().underVespaHome("conf/zookeeper/zookeeper-client.cfg"));
    private static final Duration ZK_SESSION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ZK_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BASE_SLEEP_TIME = Duration.ofSeconds(1);
    private static final int MAX_RETRIES = 10;
    private static final RetryPolicy DEFAULT_RETRY_POLICY = new ExponentialBackoffRetry((int) BASE_SLEEP_TIME.toMillis(), MAX_RETRIES);

    protected final RetryPolicy retryPolicy = DEFAULT_RETRY_POLICY;

    private final CuratorFramework curatorFramework;
    private final ConnectionSpec connectionSpec;

    // All lock keys, to allow re-entrancy. This will grow forever, but this should be too slow to be a problem
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    /** Creates a curator instance from a comma-separated string of ZooKeeper host:port strings */
    public static Curator create(String connectionSpec) {
        return new Curator(ConnectionSpec.create(connectionSpec), Optional.of(ZK_CLIENT_CONFIG_FILE));
    }

    // For testing only, use Optional.empty for clientConfigFile parameter to create default zookeeper client config
    public static Curator create(String connectionSpec, Optional<File> clientConfigFile) {
        return new Curator(ConnectionSpec.create(connectionSpec), clientConfigFile);
    }

    @Inject
    // TODO jonmv: Use a Provider for this, due to required shutdown.
    public Curator(CuratorConfig curatorConfig, @SuppressWarnings("unused") VespaZooKeeperServer server) {
        // Depends on ZooKeeperServer to make sure it is started first
        this(ConnectionSpec.create(curatorConfig.server(),
                                   CuratorConfig.Server::hostname,
                                   CuratorConfig.Server::port,
                                   curatorConfig.zookeeperLocalhostAffinity()),
             Optional.of(ZK_CLIENT_CONFIG_FILE));
    }

    // TODO: This can be removed when this package is no longer public API.
    public Curator(ConfigserverConfig configserverConfig, @SuppressWarnings("unused") VespaZooKeeperServer server) {
        this(ConnectionSpec.create(configserverConfig.zookeeperserver(),
                                   ConfigserverConfig.Zookeeperserver::hostname,
                                   ConfigserverConfig.Zookeeperserver::port,
                                   configserverConfig.zookeeperLocalhostAffinity()),
             Optional.of(ZK_CLIENT_CONFIG_FILE));
    }

    protected Curator(String connectionSpec, String zooKeeperEnsembleConnectionSpec, Function<RetryPolicy, CuratorFramework> curatorFactory) {
        this(ConnectionSpec.create(connectionSpec, zooKeeperEnsembleConnectionSpec), curatorFactory.apply(DEFAULT_RETRY_POLICY));
    }

    Curator(ConnectionSpec connectionSpec, Optional<File> clientConfigFile) {
        this(connectionSpec,
             CuratorFrameworkFactory
                     .builder()
                     .retryPolicy(DEFAULT_RETRY_POLICY)
                     .sessionTimeoutMs((int) ZK_SESSION_TIMEOUT.toMillis())
                     .connectionTimeoutMs((int) ZK_CONNECTION_TIMEOUT.toMillis())
                     .connectString(connectionSpec.local())
                     .zookeeperFactory(new VespaZooKeeperFactory(createClientConfig(clientConfigFile)))
                     .dontUseContainerParents() // TODO: Remove when we know ZooKeeper 3.5 works fine, consider waiting until Vespa 8
                     .build());
    }

    private Curator(ConnectionSpec connectionSpec, CuratorFramework curatorFramework) {
        this.connectionSpec = Objects.requireNonNull(connectionSpec);
        this.curatorFramework = Objects.requireNonNull(curatorFramework);
        addLoggingListener();
        curatorFramework.start();
    }

    private static ZKClientConfig createClientConfig(Optional<File> clientConfigFile) {
        if (clientConfigFile.isPresent()) {
            try {
                return new ZkClientConfigBuilder().toConfig(clientConfigFile.get().toPath());
            } catch (QuorumPeerConfig.ConfigException e) {
                throw new RuntimeException("Unable to create ZooKeeper client config file " + clientConfigFile.get());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return new ZKClientConfig();
        }
    }

    /**
     * Returns the ZooKeeper "connect string" used by curator: a comma-separated list of
     * host:port of ZooKeeper endpoints to connect to. This may be a subset of
     * zooKeeperEnsembleConnectionSpec() if there's some affinity, e.g. for
     * performance reasons.
     *
     * This may be empty but never null 
     */
    public String connectionSpec() { return connectionSpec.local(); }

    /** For internal use; prefer creating a {@link CuratorCounter} */
    public DistributedAtomicLong createAtomicCounter(String path) {
        return new DistributedAtomicLong(curatorFramework, path, new ExponentialBackoffRetry((int) BASE_SLEEP_TIME.toMillis(), MAX_RETRIES));
    }

    /** For internal use; prefer creating a {@link com.yahoo.vespa.curator.Lock} */
    public InterProcessLock createMutex(String lockPath) {
        return new InterProcessMutex(curatorFramework, lockPath);
    }

    private void addLoggingListener() {
        curatorFramework.getConnectionStateListenable().addListener((curatorFramework, connectionState) -> {
            switch (connectionState) {
                case SUSPENDED: LOG.info("ZK connection state change: SUSPENDED"); break;
                case RECONNECTED: LOG.info("ZK connection state change: RECONNECTED"); break;
                case LOST: LOG.warning("ZK connection state change: LOST"); break;
            }
        });
    }

    public CompletionWaiter getCompletionWaiter(Path waiterPath, int numMembers, String id) {
        return CuratorCompletionWaiter.create(this, waiterPath, id);
    }

    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, int numMembers, String id) {
        return CuratorCompletionWaiter.createAndInitialize(this, parentPath, waiterNode, id);
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
    // TODO: Use create().orSetData() in Curator 4 and later
    public void set(Path path, byte[] data) {
        if ( ! exists(path))
            create(path);

        String absolutePath = path.getAbsolute();
        try {
            framework().setData().forPath(absolutePath, data);
        } catch (Exception e) {
            throw new RuntimeException("Could not set data at " + absolutePath, e);
        }
    }

    /**
     * Creates an empty node at a path, creating any parents as necessary.
     * If the node already exists nothing is done.
     * Returns whether a change was attempted.
     */
    public boolean create(Path path) {
        if (exists(path)) return false;

        String absolutePath = path.getAbsolute();
        try {
            framework().create().creatingParentsIfNeeded().forPath(absolutePath, new byte[0]);
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            // Path created between exists() and create() call, do nothing
        } catch (Exception e) {
            throw new RuntimeException("Could not create " + absolutePath, e);
        }
        return true;
    }

    /**
     * Creates all the given paths in a single transaction. Any paths which already exists are ignored.
     */
    public void createAtomically(Path... paths) {
        try {
            @SuppressWarnings("deprecation") CuratorTransaction transaction = framework().inTransaction();
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
        try {
            framework().delete().guaranteed().deletingChildrenIfNeeded().forPath(path.getAbsolute());
        } catch (KeeperException.NoNodeException e) {
            // Do nothing
        } catch (Exception e) {
            throw new RuntimeException("Could not delete " + path.getAbsolute(), e);
        }
    }

    /**
     * Returns the names of the children at the given path.
     * If the path does not exist or have no children an empty list (never null) is returned.
     */
    public List<String> getChildren(Path path) {
        try {
            return framework().getChildren().forPath(path.getAbsolute());
        } catch (KeeperException.NoNodeException e) {
            return List.of();
        } catch (Exception e) {
            throw new RuntimeException("Could not get children of " + path.getAbsolute(), e);
        }
    }

    /**
     * Returns the data at the given path, which may be a zero-length buffer if the node exists but have no data.
     * Empty is returned if the path does not exist.
     */
    public Optional<byte[]> getData(Path path) {
        try {
            return Optional.of(framework().getData().forPath(path.getAbsolute()));
        }
        catch (KeeperException.NoNodeException e) {
            return Optional.empty();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not get data at " + path.getAbsolute(), e);
        }
    }

    /**
     * Returns the stat data at the given path.
     * Empty is returned if the path does not exist.
     */
    public Optional<Stat> getStat(Path path) {
        try {
            return Optional.ofNullable(framework().checkExists().forPath(path.getAbsolute()));
        }
        catch (KeeperException.NoNodeException e) {
            return Optional.empty();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not get data at " + path.getAbsolute(), e);
        }
    }

    /** Create and acquire a re-entrant lock in given path */
    public Lock lock(Path path, Duration timeout) {
        create(path);
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), this));
        lock.acquire(timeout);
        return lock;
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

        /** Returns the ChildData, or null if it does not exist. */
        ChildData getCurrentData(Path absolutePath);

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
        return connectionSpec.ensemble();
    }

    /**
     * Returns the number of zooKeeper servers in this ensemble.
     * WARNING: This may be different from the number of servers this Curator may connect to.
     * TODO: Move method out of this class.
     */
    public int zooKeeperEnsembleCount() { return connectionSpec.ensembleSize(); }

}
