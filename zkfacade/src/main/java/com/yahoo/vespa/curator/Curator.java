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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

    private static final int baseSleepTime = 1000; //ms
    private static final int maxRetries = 10;

    private final CuratorFramework curatorFramework;
    protected final RetryPolicy retryPolicy;

    private final String connectionSpec;
    private final int serverCount;

    /** Creates a curator instance from a comma-separated string of ZooKeeper host:port strings */
    public static Curator create(String connectionSpec) {
        return new Curator(connectionSpec);
    }

    // Depend on ZooKeeperServer to make sure it is started first
    // TODO: Move zookeeperserver config out of configserverconfig (requires update of controller services.xml as well)
    @Inject
    public Curator(ConfigserverConfig configserverConfig, ZooKeeperServer server) {
        this(createConnectionSpec(configserverConfig));
    }
    
    static String createConnectionSpec(ConfigserverConfig config) {
        String thisServer = HostName.getLocalhost();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < config.zookeeperserver().size(); i++) {
            ConfigserverConfig.Zookeeperserver server = config.zookeeperserver(i);

            String spec = String.format("%s:%d", server.hostname(), server.port());

            if ((config.zookeeperLocalhostAffinity() || config.system().equals("cd")) && server.hostname().equals(thisServer)) {
                // Only connect to localhost server if possible, to save network traffic
                // and balance load.
                return spec;
            }

            if (sb.length() > 0) {
                sb.append(',');
            }

            sb.append(spec);
        }
        return sb.toString();
    }

    /** 
     * Create a curator instance which connects to the zookeeper servers given by a connection spec
     * on the format "hostname1:port,hostname2:port" ...
     */
    public Curator(String connectionSpec) {
        Objects.requireNonNull(connectionSpec, "The curator connection spec cannot be null");
        this.connectionSpec = connectionSpec;
        this.serverCount = connectionSpec.split(",").length;
        validateConnectionSpec(connectionSpec);
        retryPolicy = new ExponentialBackoffRetry(baseSleepTime, maxRetries);
        curatorFramework = CuratorFrameworkFactory.builder()
                .retryPolicy(retryPolicy)
                .sessionTimeoutMs(ZK_SESSION_TIMEOUT)
                .connectionTimeoutMs(ZK_CONNECTION_TIMEOUT)
                .connectString(connectionSpec)
                .zookeeperFactory(new DNSResolvingFixerZooKeeperFactory(UNKNOWN_HOST_TIMEOUT_MILLIS))
                .build();
        addFakeListener();
        curatorFramework.start();
    }

    protected Curator() {
        this.connectionSpec = "";
        this.serverCount = 0;
        retryPolicy = new ExponentialBackoffRetry(baseSleepTime, maxRetries);
        curatorFramework = null;
    }

    private static void validateConnectionSpec(String connectionSpec) {
        if (connectionSpec == null || connectionSpec.isEmpty())
            throw new IllegalArgumentException(String.format("Connections spec '%s' is not valid", connectionSpec));
    }

    /** Returns the number of zooKeeper servers in this cluster */
    public int serverCount() { return serverCount; }

    /** 
     * Returns the servers in this cluster as a comma-separated list of host:port strings. 
     * This may be empty but never null 
     */
    public String connectionSpec() { return connectionSpec; }

    /** For internal use; prefer creating a {@link CuratorCounter} */
    public DistributedAtomicLong createAtomicCounter(String path) {
        return new DistributedAtomicLong(curatorFramework, path, new ExponentialBackoffRetry(baseSleepTime, maxRetries));
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

}
