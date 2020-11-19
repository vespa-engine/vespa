// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.impl;

import com.google.inject.Inject;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Implementation of Vespa's interface for Curator.
 *
 * @author mpolden
 */
public class VespaCurator implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(VespaCurator.class.getName());
    private static final String ZK_CLIENT_CONFIG_FILE = Defaults.getDefaults().underVespaHome("conf/zookeeper/zookeeper-client.cfg");
    private static final Duration ZK_SESSION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ZK_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BASE_SLEEP_TIME = Duration.ofSeconds(1);
    private static final int MAX_RETRIES = 10;
    public static final RetryPolicy RETRY_POLICY = new ExponentialBackoffRetry((int) BASE_SLEEP_TIME.toMillis(),
                                                                               MAX_RETRIES);

    private final CuratorFramework curatorFramework;
    private final String connectionSpec; // May be a subset of the servers in the ensemble
    private final String ensembleConnectionSpec;
    private final int zooKeeperEnsembleCount;

    // All lock keys, to allow re-entrancy. This will grow forever, but this should be too slow to be a problem
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    @Inject
    public VespaCurator(CuratorConfig config, VespaZooKeeperServer zooKeeperServer) {
        // Depends on VespaZooKeeperServer to ensure that it's constructed/started before this
        this(createConnectionSpec(config, false), createConnectionSpec(config, true));
    }

    public VespaCurator(String connectionSpec, String ensembleConnectionSpec) {
        this(connectionSpec, ensembleConnectionSpec, Optional.of(Paths.get(ZK_CLIENT_CONFIG_FILE)));
    }

    public VespaCurator(String connectionSpec, String ensembleConnectionSpec,
                        Optional<java.nio.file.Path> clientConfigFile) {
        this(connectionSpec, ensembleConnectionSpec, CuratorFrameworkFactory.builder()
                                                       .retryPolicy(RETRY_POLICY)
                                                       .sessionTimeoutMs((int) ZK_SESSION_TIMEOUT.toMillis())
                                                       .connectionTimeoutMs((int) ZK_CONNECTION_TIMEOUT.toMillis())
                                                       .connectString(connectionSpec)
                                                       .zookeeperFactory(new VespaZooKeeperFactory(createClientConfig(clientConfigFile)))
                                                       .dontUseContainerParents() // TODO: Remove when we know ZooKeeper 3.5 works fine, consider waiting until Vespa 8
                                                       .build());
    }

    public VespaCurator(String connectionSpec, String ensembleConnectionSpec, CuratorFramework curatorFramework) {
        this.connectionSpec = connectionSpec;
        this.curatorFramework = curatorFramework;
        this.ensembleConnectionSpec = ensembleConnectionSpec;
        this.zooKeeperEnsembleCount = ensembleConnectionSpec.split(",").length;
        validateConnectionSpec(connectionSpec);
        validateConnectionSpec(ensembleConnectionSpec);
        addLoggingListener();
        curatorFramework.start();
    }

    private static String createConnectionSpec(CuratorConfig config, boolean ensemble) {
        String thisServer = HostName.getLocalhost();
        StringBuilder sb = new StringBuilder();
        for (CuratorConfig.Server server : config.server()) {
            if (!ensemble && config.zookeeperLocalhostAffinity() && !thisServer.equals(server.hostname())) continue;
            sb.append(server.hostname());
            sb.append(':');
            sb.append(server.port());
            sb.append(',');
        }
        sb.setLength(sb.length() - 1); // Remove trailing comma
        String connectionSpec = sb.toString();
        if (!ensemble && connectionSpec.isEmpty()) {
            throw new IllegalArgumentException("Unable to create connect string to localhost: " +
                                               "There is no localhost server specified in config: " + config);
        }
        return connectionSpec;
    }

    private static ZKClientConfig createClientConfig(Optional<java.nio.file.Path> clientConfigFile) {
        if (clientConfigFile.isPresent()) {
            boolean useSecureClient = Boolean.parseBoolean(getEnvironmentVariable("VESPA_USE_TLS_FOR_ZOOKEEPER_CLIENT").orElse("false"));
            String config = "zookeeper.client.secure=" + useSecureClient + "\n";
            clientConfigFile.get().toFile().getParentFile().mkdirs();
            IOUtils.writeFile(clientConfigFile.get().toFile(), Utf8.toBytes(config));
            try {
                return new ZKClientConfig(clientConfigFile.get().toFile());
            } catch (QuorumPeerConfig.ConfigException e) {
                throw new RuntimeException("Unable to create ZooKeeper client config file " + clientConfigFile.get());
            }
        } else {
            return new ZKClientConfig();
        }
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

    public Curator.CompletionWaiter getCompletionWaiter(Curator curator, Path waiterPath, String id) {
        return CuratorCompletionWaiter.create(curator, waiterPath, id);
    }

    public Curator.CompletionWaiter createCompletionWaiter(Curator curator, Path parentPath, String waiterNode, String id) {
        return CuratorCompletionWaiter.createAndInitialize(curator, parentPath, waiterNode, id);
    }

    /** Creates a listenable cache which keeps in sync with changes to all the immediate children of a path */
    public Curator.DirectoryCache createDirectoryCache(String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        return new PathChildrenCacheWrapper(framework(), path, cacheData, dataIsCompressed, executorService);
    }

    /** Creates a listenable cache which keeps in sync with changes to a given node */
    public Curator.FileCache createFileCache(String path, boolean dataIsCompressed) {
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
        return lock(path, timeout, this::createMutex);
    }

    /** Create and acquire a re-entrant lock in given path */
    public Lock lock(Path path, Duration timeout, Function<String, InterProcessLock> mutexFactory) {
        create(path);
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), mutexFactory.apply(pathArg.getAbsolute())));
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
     * @return The non-null connect string containing all ZooKeeper servers in the ensemble.
     * WARNING: This may be different from the servers this Curator may connect to.
     * TODO: Move method out of this class.
     */
    public String zooKeeperEnsembleConnectionSpec() {
        return ensembleConnectionSpec;
    }

    /**
     * Returns the number of zooKeeper servers in this ensemble.
     * WARNING: This may be different from the number of servers this Curator may connect to.
     * TODO: Move method out of this class.
     */
    public int zooKeeperEnsembleCount() { return zooKeeperEnsembleCount; }

    private static Optional<String> getEnvironmentVariable(String variableName) {
        return Optional.ofNullable(System.getenv().get(variableName))
                       .filter(var -> !var.isEmpty());
    }

}
