// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Curator backed database for storing working state shared between controller servers.
 * This maps controller specific operations to general curator operations.
 *
 * @author bratseth
 */
public class CuratorDb {

    /** Use a nonstandard zk port to avoid interfering with connection to the config server zk cluster */
    private static final int zooKeeperPort = 2281;

    private static final Logger log = Logger.getLogger(CuratorDb.class.getName());

    private static final Path root = Path.fromString("/controller/v1");

    private static final Duration defaultLockTimeout = Duration.ofMinutes(5);

    private final StringSetSerializer stringSetSerializer = new StringSetSerializer();
    private final JobQueueSerializer jobQueueSerializer = new JobQueueSerializer();

    @SuppressWarnings("unused") // This server is used (only) from the curator instance of this over the network */
    private final ZooKeeperServer zooKeeperServer;

    private final Curator curator;

    /**
     * All keys, to allow reentrancy.
     * This will grow forever, but this should be too slow to be a problem.
     */
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    /** Create a curator db which also set up a ZooKeeper server (such that this instance is both client and server) */
    @Inject
    public CuratorDb(ClusterInfoConfig clusterInfo) {
        this.zooKeeperServer = new ZooKeeperServer(toZookeeperServerConfig(clusterInfo));
        this.curator = new Curator(toConnectionSpec(clusterInfo));
    }

    /** Create a curator db which does not set up a server, using the given Curator instance */
    protected CuratorDb(Curator curator) {
        this.zooKeeperServer = null;
        this.curator = curator;
    }

    private static ZookeeperServerConfig toZookeeperServerConfig(ClusterInfoConfig clusterInfo) {
        ZookeeperServerConfig.Builder b = new ZookeeperServerConfig.Builder();
        b.zooKeeperConfigFile("conf/zookeeper/controller-zookeeper.cfg");
        b.dataDir("var/controller-zookeeper");
        b.clientPort(zooKeeperPort);
        b.myidFile("var/controller-zookeeper/myid");
        b.myid(myIndex(clusterInfo));

        for (ClusterInfoConfig.Services clusterMember : clusterInfo.services()) {
            ZookeeperServerConfig.Server.Builder server = new ZookeeperServerConfig.Server.Builder();
            server.id(clusterMember.index());
            server.hostname(clusterMember.hostname());
            server.quorumPort(zooKeeperPort + 1);
            server.electionPort(zooKeeperPort + 2);
            b.server(server);
        }
        return new ZookeeperServerConfig(b);
    }

    private static Integer myIndex(ClusterInfoConfig clusterInfo) {
        String hostname = HostName.getLocalhost();
        return clusterInfo.services().stream()
                .filter(service -> service.hostname().equals(hostname))
                .map(ClusterInfoConfig.Services::index)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to find index for this node by hostname '" +
                                                             hostname + "'"));
    }

    private static String toConnectionSpec(ClusterInfoConfig clusterInfo) {
        return clusterInfo.services().stream()
                .map(member -> member.hostname() + ":" + zooKeeperPort)
                .collect(Collectors.joining(","));
    }

    // -------------- Locks --------------------------------------------------

    public Lock lock(TenantId id, Duration timeout) {
        return lock(lockPath(id), timeout);
    }

    public Lock lock(ApplicationId id, Duration timeout) {
        return lock(lockPath(id), timeout);
    }

    /** Create a reentrant lock */
    private Lock lock(Path path, Duration timeout) {
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), curator));
        lock.acquire(timeout);
        return lock;
    }

    public Lock lockInactiveJobs() {
        return lock(root.append("locks").append("inactiveJobsLock"), defaultLockTimeout);
    }

    public Lock lockJobQueues() {
        return lock(root.append("locks").append("jobQueuesLock"), defaultLockTimeout);
    }

    public Lock lockMaintenanceJob(String jobName) {
        // Use a short timeout such that if maintenance jobs are started at about the same time on different nodes
        // and the maintenance job takes a long time to complete, only one of the nodes will run the job
        // in each maintenance interval
        return lock(root.append("locks").append("maintenanceJobLocks").append(jobName), Duration.ofSeconds(1));
    }

    public Lock lockProvisionState(String provisionStateId) {
        return lock(lockPath(provisionStateId), Duration.ofSeconds(1));
    }

    public Lock lockVespaServerPool() {
        return lock(root.append("locks").append("vespaServerPoolLock"), Duration.ofSeconds(1));
    }

    public Lock lockOpenStackServerPool() {
        return lock(root.append("locks").append("openStackServerPoolLock"), Duration.ofSeconds(1));
    }

    // -------------- Read and write --------------------------------------------------

    public Set<String> readInactiveJobs() {
        try {
            Optional<byte[]> data = curator.getData(inactiveJobsPath());
            if (! data.isPresent() || data.get().length == 0) return new HashSet<>(); // inactive jobs has never been written
            return stringSetSerializer.fromJson(data.get());
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Error reading inactive jobs, deleting inactive state");
            writeInactiveJobs(Collections.emptySet());
            return new HashSet<>();
        }
    }

    public void writeInactiveJobs(Set<String> inactiveJobs) {
        NestedTransaction transaction = new NestedTransaction();
        curator.set(inactiveJobsPath(), stringSetSerializer.toJson(inactiveJobs));
        transaction.commit();
    }

    public Deque<ApplicationId> readJobQueue(DeploymentJobs.JobType jobType) {
        try {
            Optional<byte[]> data = curator.getData(jobQueuePath(jobType));
            if (! data.isPresent() || data.get().length == 0) return new ArrayDeque<>(); // job queue has never been written
            return jobQueueSerializer.fromJson(data.get());
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Error reading job queue, deleting inactive state");
            writeInactiveJobs(Collections.emptySet());
            return new ArrayDeque<>();
        }
    }

    public void writeJobQueue(DeploymentJobs.JobType jobType, Deque<ApplicationId> queue) {
        NestedTransaction transaction = new NestedTransaction();
        curator.set(jobQueuePath(jobType), jobQueueSerializer.toJson(queue));
        transaction.commit();
    }

    public double readUpgradesPerMinute() {
        Optional<byte[]> n = curator.getData(upgradesPerMinutePath());
        if (!n.isPresent() || n.get().length == 0) {
            return 0.5; // Default if value has never been written
        }
        return ByteBuffer.wrap(n.get()).getDouble();
    }

    public void writeUpgradesPerMinute(double n) {
        if (n < 0) {
            throw new IllegalArgumentException("Upgrades per minute must be >= 0");
        }
        NestedTransaction transaction = new NestedTransaction();
        curator.set(upgradesPerMinutePath(), ByteBuffer.allocate(Double.BYTES).putDouble(n).array());
        transaction.commit();
    }

    public boolean readIgnoreConfidence() {
        Optional<byte[]> value = curator.getData(ignoreConfidencePath());
        if (! value.isPresent() || value.get().length == 0) {
            return false; // Default if value has never been written
        }
        return ByteBuffer.wrap(value.get()).getInt() == 1;
    }

    public void writeIgnoreConfidence(boolean value) {
        NestedTransaction transaction = new NestedTransaction();
        curator.set(ignoreConfidencePath(), ByteBuffer.allocate(Integer.BYTES).putInt(value ? 1 : 0).array());
        transaction.commit();
    }

    public void writeVersionStatus(VersionStatus status) {
        VersionStatusSerializer serializer = new VersionStatusSerializer();
        NestedTransaction transaction = new NestedTransaction();
        try {
            // TODO: Removes unused data. Remove after October 2017
            if (curator.getData(systemVersionPath()).isPresent()) {
                curator.delete(systemVersionPath());
            }
            curator.set(versionStatusPath(), SlimeUtils.toJsonBytes(serializer.toSlime(status)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize version status", e);
        }
        transaction.commit();
    }

    public VersionStatus readVersionStatus() {
        Optional<byte[]> data = curator.getData(versionStatusPath());
        if (!data.isPresent() || data.get().length == 0) {
            return VersionStatus.empty(); // Default if status has never been written
        }
        VersionStatusSerializer serializer = new VersionStatusSerializer();
        return serializer.fromSlime(SlimeUtils.jsonToSlime(data.get()));
    }

    public Optional<byte[]> readProvisionState(String provisionId) {
        return curator.getData(provisionStatePath(provisionId));
    }

    public void writeProvisionState(String provisionId, byte[] data) {
        curator.set(provisionStatePath(provisionId), data);
    }

    public List<String> readProvisionStateIds() {
        return curator.getChildren(provisionStatePath());
    }

    public Optional<byte[]> readVespaServerPool() {
        return curator.getData(vespaServerPoolPath());
    }

    public void writeVespaServerPool(byte[] data) {
        curator.set(vespaServerPoolPath(), data);
    }

    public Optional<byte[]> readOpenStackServerPool() {
        return curator.getData(openStackServerPoolPath());
    }

    public void writeOpenStackServerPool(byte[] data) {
        curator.set(openStackServerPoolPath(), data);
    }

    // -------------- Paths --------------------------------------------------

    private Path systemVersionPath() {
        return root.append("systemVersion");
    }

    private Path lockPath(TenantId tenant) {
        Path lockPath = root.append("locks")
                .append(tenant.id());
        curator.create(lockPath);
        return lockPath;
    }

    private Path lockPath(ApplicationId application) {
        Path lockPath = root.append("locks")
                .append(application.tenant().value())
                .append(application.application().value())
                .append(application.instance().value());
        curator.create(lockPath);
        return lockPath;
    }

    private Path lockPath(String provisionId) {
        Path lockPath = root.append("locks")
                .append(provisionStatePath());
        curator.create(lockPath);
        return lockPath;
    }

    private Path inactiveJobsPath() {
        return root.append("inactiveJobs");
    }

    private Path jobQueuePath(DeploymentJobs.JobType jobType) {
        return root.append("jobQueues").append(jobType.name());
    }

    private Path upgradesPerMinutePath() {
        return root.append("upgrader").append("upgradesPerMinute");
    }

    private Path ignoreConfidencePath() {
        return root.append("upgrader").append("ignoreConfidence");
    }

    private Path versionStatusPath() { return root.append("versionStatus"); }

    private Path provisionStatePath() {
        return root.append("provisioning").append("states");
    }

    private Path provisionStatePath(String provisionId) {
        return provisionStatePath().append(provisionId);
    }

    private Path vespaServerPoolPath() {
        return root.append("vespaServerPool");
    }

    private Path openStackServerPoolPath() {
        return root.append("openStackServerPool");
    }
}
