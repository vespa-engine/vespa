// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.impl.VespaCurator;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.zookeeper.data.Stat;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Curator interface for Vespa.
 *
 * This contains method for constructing common recipes and utilities as well as
 * a small wrapper API for common operations which uses typed paths and avoids throwing checked exceptions.
 *
 * There is a mock implementation in MockCurator.
 *
 * @author vegardh
 * @author bratseth
 */
// TODO: Remove this on Vespa 8
@Deprecated // Use com.yahoo.vespa.curator.impl.VespaCurator instead
public class Curator implements AutoCloseable {

    protected final RetryPolicy retryPolicy = VespaCurator.RETRY_POLICY;

    private final VespaCurator vespaCurator;

    /** Creates a curator instance from a comma-separated string of ZooKeeper host:port strings */
    public static Curator create(String connectionSpec) {
        return new Curator(connectionSpec, connectionSpec);
    }

    // For testing only, use Optional.empty for clientConfigFile parameter to create default zookeeper client config
    public static Curator create(String connectionSpec, Optional<File> clientConfigFile) {
        return new Curator(connectionSpec, connectionSpec, clientConfigFile);
    }

    // Depend on ZooKeeperServer to make sure it is started first
    @Inject
    public Curator(ConfigserverConfig configserverConfig, VespaZooKeeperServer server) {
        this.vespaCurator = new VespaCurator(createConnectionSpec(configserverConfig), createEnsembleConnectionSpec(configserverConfig));
    }

    Curator(String connectionSpec, String ensembleConnectionSpec, Optional<File> clientConfigFile) {
        this.vespaCurator = new VespaCurator(connectionSpec, ensembleConnectionSpec, clientConfigFile.map(File::toPath));
    }

    protected Curator(String connectionSpec, String ensembleConnectionSpec, Function<RetryPolicy, CuratorFramework> curatorFrameworkFactory) {
        this.vespaCurator = new VespaCurator(connectionSpec, ensembleConnectionSpec, curatorFrameworkFactory.apply(VespaCurator.RETRY_POLICY));
    }

    private Curator(String connectionSpec, String ensembleConnectionSpec) {
        this.vespaCurator = new VespaCurator(connectionSpec, ensembleConnectionSpec);
    }

    static String createConnectionSpec(ConfigserverConfig configserverConfig) {
        return configserverConfig.zookeeperLocalhostAffinity()
                ? createConnectionSpecForLocalhost(configserverConfig)
                : createEnsembleConnectionSpec(configserverConfig);
    }

    static String createEnsembleConnectionSpec(ConfigserverConfig config) {
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

    static String createConnectionSpecForLocalhost(ConfigserverConfig config) {
        String thisServer = HostName.getLocalhost();

        for (int i = 0; i < config.zookeeperserver().size(); i++) {
            ConfigserverConfig.Zookeeperserver server = config.zookeeperserver(i);
            if (thisServer.equals(server.hostname())) {
                return String.format("%s:%d", server.hostname(), server.port());
            }
        }

        throw new IllegalArgumentException("Unable to create connect string to localhost: " +
                "There is no localhost server specified in config: " + config);
    }

    public String connectionSpec() {
        return vespaCurator.connectionSpec();
    }

    public DistributedAtomicLong createAtomicCounter(String path) {
        return vespaCurator.createAtomicCounter(path);
    }

    public InterProcessLock createMutex(String lockPath) {
        return vespaCurator.createMutex(lockPath);
    }

    public CompletionWaiter getCompletionWaiter(Path waiterPath, int numMembers, String id) {
        return vespaCurator.getCompletionWaiter(this, waiterPath, id);
    }

    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, int numMembers, String id) {
        return vespaCurator.createCompletionWaiter(this, parentPath, waiterNode, id);
    }

    public DirectoryCache createDirectoryCache(String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        return vespaCurator.createDirectoryCache(path, cacheData, dataIsCompressed, executorService);
    }

    public FileCache createFileCache(String path, boolean dataIsCompressed) {
        return vespaCurator.createFileCache(path, dataIsCompressed);
    }

    public boolean exists(Path path) {
        return vespaCurator.exists(path);
    }

    public void set(Path path, byte[] data) {
        vespaCurator.set(path, data);
    }

    public boolean create(Path path) {
        return vespaCurator.create(path);
    }

    public void createAtomically(Path... paths) {
        vespaCurator.createAtomically(paths);
    }

    public void delete(Path path) {
        vespaCurator.delete(path);
    }

    public List<String> getChildren(Path path) {
        return vespaCurator.getChildren(path);
    }

    public Optional<byte[]> getData(Path path) {
        return vespaCurator.getData(path);
    }

    public Optional<Stat> getStat(Path path) {
        return vespaCurator.getStat(path);
    }

    public Lock lock(Path path, Duration timeout) {
        return vespaCurator.lock(path, timeout, this::createMutex);
    }

    public CuratorFramework framework() {
        return vespaCurator.framework();
    }

    @Override
    public void close() {
        vespaCurator.close();
    }

    public String zooKeeperEnsembleConnectionSpec() {
        return vespaCurator.zooKeeperEnsembleConnectionSpec();
    }

    public int zooKeeperEnsembleCount() {
        return vespaCurator.zooKeeperEnsembleCount();
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

}
