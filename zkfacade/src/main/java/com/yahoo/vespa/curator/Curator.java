// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.EphemeralType;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
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
public class Curator extends AbstractComponent implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Curator.class.getName());
    private static final File ZK_CLIENT_CONFIG_FILE = new File(Defaults.getDefaults().underVespaHome("var/zookeeper/conf/zookeeper-client.cfg"));

    // Note that session timeout has min and max values are related to tickTime defined by server, see zookeeper-server.def
    static final Duration DEFAULT_ZK_SESSION_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration ZK_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BASE_SLEEP_TIME = Duration.ofSeconds(1);
    private static final int MAX_RETRIES = 4;
    private static final RetryPolicy DEFAULT_RETRY_POLICY = new ExponentialBackoffRetry((int) BASE_SLEEP_TIME.toMillis(), MAX_RETRIES);
    // Default value taken from ZookeeperServerConfig
    static final long defaultJuteMaxBuffer = Long.parseLong(System.getProperty("jute.maxbuffer", "52428800"));

    private final CuratorFramework curatorFramework;
    private final ConnectionSpec connectionSpec;
    private final long juteMaxBuffer;
    private final Duration sessionTimeout;

    // All lock keys, to allow re-entrancy. This will grow forever, but this should be too slow to be a problem
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    /** Creates a curator instance from a comma-separated string of ZooKeeper host:port strings */
    public static Curator create(String connectionSpec) {
        return new Curator(ConnectionSpec.create(connectionSpec), Optional.of(ZK_CLIENT_CONFIG_FILE),
                           defaultJuteMaxBuffer, DEFAULT_ZK_SESSION_TIMEOUT);
    }

    // For testing only, use Optional.empty for clientConfigFile parameter to create default zookeeper client config
    public static Curator create(String connectionSpec, Optional<File> clientConfigFile) {
        return new Curator(ConnectionSpec.create(connectionSpec), clientConfigFile,
                           defaultJuteMaxBuffer, DEFAULT_ZK_SESSION_TIMEOUT);
    }

    @Inject
    public Curator(CuratorConfig curatorConfig, @SuppressWarnings("unused") VespaZooKeeperServer server) {
        // Depends on ZooKeeperServer to make sure it is started first
        this(ConnectionSpec.create(curatorConfig.server(),
                                   CuratorConfig.Server::hostname,
                                   CuratorConfig.Server::port,
                                   curatorConfig.zookeeperLocalhostAffinity()),
             Optional.of(ZK_CLIENT_CONFIG_FILE),
             defaultJuteMaxBuffer,
             Duration.ofSeconds(curatorConfig.zookeeperSessionTimeoutSeconds()));
    }

    protected Curator(String connectionSpec, String zooKeeperEnsembleConnectionSpec, Function<RetryPolicy, CuratorFramework> curatorFactory) {
        this(ConnectionSpec.create(connectionSpec, zooKeeperEnsembleConnectionSpec), curatorFactory.apply(DEFAULT_RETRY_POLICY),
             defaultJuteMaxBuffer, DEFAULT_ZK_SESSION_TIMEOUT);
    }

    Curator(ConnectionSpec connectionSpec, Optional<File> clientConfigFile, long juteMaxBuffer, Duration sessionTimeout) {
        this(connectionSpec,
             CuratorFrameworkFactory
                     .builder()
                     .retryPolicy(DEFAULT_RETRY_POLICY)
                     .sessionTimeoutMs((int) sessionTimeout.toMillis())
                     .connectionTimeoutMs((int) ZK_CONNECTION_TIMEOUT.toMillis())
                     .connectString(connectionSpec.local())
                     .zookeeperFactory(new VespaZooKeeperFactory(createClientConfig(clientConfigFile)))
                     .dontUseContainerParents() // TODO: Consider changing this in Vespa 9
                     .ensembleTracker(false)
                     .build(),
             juteMaxBuffer,
             sessionTimeout);
    }

    private Curator(ConnectionSpec connectionSpec, CuratorFramework curatorFramework, long juteMaxBuffer, Duration sessionTimeout) {
        this.connectionSpec = Objects.requireNonNull(connectionSpec);
        this.curatorFramework = Objects.requireNonNull(curatorFramework);
        this.juteMaxBuffer = juteMaxBuffer;
        this.sessionTimeout = sessionTimeout;
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

    public Duration sessionTimeout() {
        return sessionTimeout;
    }

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

    public CompletionWaiter getCompletionWaiter(Path waiterPath, String id, Duration waitForAll) {
        return CuratorCompletionWaiter.create(this, waiterPath, id, waitForAll);
    }

    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, String id, Duration waitForAll) {
        return CuratorCompletionWaiter.createAndInitialize(this, parentPath, waiterNode, id, waitForAll);
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
    public Stat set(Path path, byte[] data) {
        return set(path, data, -1);
    }

    public Stat set(Path path, byte[] data, int expectedVersion) {
        if (data.length > juteMaxBuffer)
            throw new IllegalArgumentException("Cannot not set data at " + path.getAbsolute() + ", " +
                                               data.length + " bytes is too much, max number of bytes allowed per node is " + juteMaxBuffer);

        if ( ! exists(path))
            create(path);

        String absolutePath = path.getAbsolute();
        try {
            return framework().setData().withVersion(expectedVersion).forPath(absolutePath, data);
        } catch (Exception e) {
            throw new RuntimeException("Could not set data at " + absolutePath, e);
        }
    }


    /** @see #create(Path, Duration) */
    public boolean create(Path path) { return create(path, null); }

    /**
     * Creates an empty node at a path, creating any parents as necessary.
     * If the node already exists nothing is done.
     * Returns whether a change was attempted.
     */
    public boolean create(Path path, Duration ttl) {
        return create(path, ttl, null);
    }
    private boolean create(Path path, Duration ttl, Stat stat) {
        if (exists(path)) return false;

        String absolutePath = path.getAbsolute();
        try {
            CreateBuilder b = framework().create();
            if (ttl != null) {
                long millis = ttl.toMillis();
                if (millis <= 0 || millis > EphemeralType.TTL.maxValue())
                    throw new IllegalArgumentException(ttl.toString());
                b.withTtl(millis).withMode(CreateMode.PERSISTENT_WITH_TTL);
            }
            if (stat == null) b.creatingParentsIfNeeded()                    .forPath(absolutePath, new byte[0]);
            else              b.creatingParentsIfNeeded().storingStatIn(stat).forPath(absolutePath, new byte[0]);
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
     * Deletes the path and any children it may have.
     * If the path does not exist, nothing is done.
     */
    public void delete(Path path) {
        delete(path, true);
    }

    /**
     * Deletes the path and any children it may have.
     * If the path does not exist, nothing is done.
     */
    public void delete(Path path, boolean recursive) {
        delete(path, -1, recursive);
    }

    public void delete(Path path, int expectedVersion, boolean recursive) {
        try {
            if (recursive) framework().delete().guaranteed().deletingChildrenIfNeeded().withVersion(expectedVersion).forPath(path.getAbsolute());
            else           framework().delete().guaranteed()                           .withVersion(expectedVersion).forPath(path.getAbsolute());
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
        return getData(path, null);
    }

    public Optional<byte[]> getData(Path path, Stat stat) {
        try {
            return stat == null ? Optional.of(framework().getData()                    .forPath(path.getAbsolute()))
                                : Optional.of(framework().getData().storingStatIn(stat).forPath(path.getAbsolute()));
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

    /** Create and acquire a re-entrant lock in given path with a TTL */
    public Lock lock(Path path, Duration timeout, Duration ttl) {
        create(path, ttl);
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), this));
        lock.acquire(timeout);
        return lock;
    }

    /** Create and acquire a re-entrant lock in given path */
    public Lock lock(Path path, Duration timeout) { return lock(path, timeout, null); }

    /** Returns the curator framework API */
    public CuratorFramework framework() {
        return curatorFramework;
    }

    @Override
    public void close() {
        ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("curator-shutdown"));
        Future<?> shutdown = CompletableFuture.runAsync(curatorFramework::close, executor);
        try {
            shutdown.get(10, TimeUnit.SECONDS);
        }
        catch (Exception e) {
           LOG.log(Level.WARNING, "Failed shutting down curator framework (within 10 seconds)", e);
           if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
    }

    @Override
    public void deconstruct() {
        close();
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
