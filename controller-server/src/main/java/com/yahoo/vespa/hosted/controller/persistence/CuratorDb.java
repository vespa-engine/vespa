// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Curator backed database for storing working state shared between controller servers.
 * This maps controller specific operations to general curator operations.
 *
 * @author bratseth
 * @author mpolden
 */
public class CuratorDb {

    private static final Logger log = Logger.getLogger(CuratorDb.class.getName());
    private static final Duration defaultLockTimeout = Duration.ofMinutes(5);

    private static final Path root = Path.fromString("/controller/v1");
    private static final Path lockRoot = root.append("locks");
    private static final Path tenantRoot = root.append("tenants");
    private static final Path applicationRoot = root.append("applications");

    private final StringSetSerializer stringSetSerializer = new StringSetSerializer();
    private final VersionStatusSerializer versionStatusSerializer = new VersionStatusSerializer();
    private final ConfidenceOverrideSerializer confidenceOverrideSerializer = new ConfidenceOverrideSerializer();
    private final TenantSerializer tenantSerializer = new TenantSerializer();
    private final ApplicationSerializer applicationSerializer = new ApplicationSerializer();

    private final Curator curator;

    /**
     * All keys, to allow reentrancy.
     * This will grow forever, but this should be too slow to be a problem.
     */
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    @Inject
    public CuratorDb(Curator curator) {
        this.curator = curator;
    }

    // -------------- Locks ---------------------------------------------------

    public Lock lock(TenantName name, Duration timeout) {
        return lock(lockPath(name), timeout);
    }

    public Lock lock(ApplicationId id, Duration timeout) {
        return lock(lockPath(id), timeout);
    }

    public Lock lockRotations() {
        return lock(lockRoot.append("rotations"), defaultLockTimeout);
    }

    /** Create a reentrant lock */
    private Lock lock(Path path, Duration timeout) {
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), curator));
        lock.acquire(timeout);
        return lock;
    }

    public Lock lockInactiveJobs() {
        return lock(lockRoot.append("inactiveJobsLock"), defaultLockTimeout);
    }

    public Lock lockJobQueues() {
        return lock(lockRoot.append("jobQueuesLock"), defaultLockTimeout);
    }

    public Lock lockMaintenanceJob(String jobName) {
        // Use a short timeout such that if maintenance jobs are started at about the same time on different nodes
        // and the maintenance job takes a long time to complete, only one of the nodes will run the job
        // in each maintenance interval
        return lock(lockRoot.append("maintenanceJobLocks").append(jobName), Duration.ofSeconds(1));
    }

    @SuppressWarnings("unused") // Called by internal code
    public Lock lockProvisionState(String provisionStateId) {
        return lock(lockPath(provisionStateId), Duration.ofSeconds(1));
    }

    @SuppressWarnings("unused") // Called by internal code
    public Lock lockVespaServerPool() {
        return lock(lockRoot.append("vespaServerPoolLock"), Duration.ofSeconds(1));
    }

    @SuppressWarnings("unused") // Called by internal code
    public Lock lockOpenStackServerPool() {
        return lock(lockRoot.append("openStackServerPoolLock"), Duration.ofSeconds(1));
    }

    // -------------- Helpers ------------------------------------------

    private Optional<Slime> readSlime(Path path) {
        return curator.getData(path).filter(data -> data.length > 0).map(SlimeUtils::jsonToSlime);
    }

    // -------------- Deployment orchestration --------------------------------

    public Set<String> readInactiveJobs() {
        try {
            return readSlime(inactiveJobsPath()).map(stringSetSerializer::fromSlime).orElseGet(HashSet::new);
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Error reading inactive jobs, deleting inactive state");
            writeInactiveJobs(Collections.emptySet());
            return new HashSet<>();
        }
    }

    public void writeInactiveJobs(Set<String> inactiveJobs) {
        curator.set(inactiveJobsPath(), stringSetSerializer.toJson(inactiveJobs));
    }

    public double readUpgradesPerMinute() {
        Optional<byte[]> n = curator.getData(upgradesPerMinutePath());
        if ( ! n.isPresent() || n.get().length == 0) {
            return 0.5; // Default if value has never been written
        }
        return ByteBuffer.wrap(n.get()).getDouble();
    }

    public void writeUpgradesPerMinute(double n) {
        if (n < 0) {
            throw new IllegalArgumentException("Upgrades per minute must be >= 0");
        }
        curator.set(upgradesPerMinutePath(), ByteBuffer.allocate(Double.BYTES).putDouble(n).array());
    }
  
    public void writeVersionStatus(VersionStatus status) {
        try {
            curator.set(versionStatusPath(), SlimeUtils.toJsonBytes(versionStatusSerializer.toSlime(status)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize version status", e);
        }
    }

    public VersionStatus readVersionStatus() {
        return readSlime(versionStatusPath()).map(versionStatusSerializer::fromSlime).orElseGet(VersionStatus::empty);
    }

    public void writeConfidenceOverrides(Map<Version, VespaVersion.Confidence> overrides) {
        try {
            curator.set(confidenceOverridesPath(),
                        SlimeUtils.toJsonBytes(confidenceOverrideSerializer.toSlime(overrides)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize confidence overrides", e);
        }
    }

    public Map<Version, VespaVersion.Confidence> readConfidenceOverrides() {
        return readSlime(confidenceOverridesPath()).map(confidenceOverrideSerializer::fromSlime)
                                                   .orElseGet(Collections::emptyMap);
    }

    // -------------- Tenant --------------------------------------------------

    public void writeTenant(UserTenant tenant) {
        try {
            curator.set(tenantPath(tenant.name()), SlimeUtils.toJsonBytes(tenantSerializer.toSlime(tenant)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + tenant.toString(), e);
        }
    }

    public Optional<UserTenant> readUserTenant(TenantName name) {
        return readSlime(tenantPath(name)).map(tenantSerializer::userTenantFrom);
    }

    public void writeTenant(AthenzTenant tenant) {
        try {
            curator.set(tenantPath(tenant.name()), SlimeUtils.toJsonBytes(tenantSerializer.toSlime(tenant)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + tenant.toString(), e);
        }
    }

    public Optional<AthenzTenant> readAthenzTenant(TenantName name) {
        return readSlime(tenantPath(name)).map(tenantSerializer::athenzTenantFrom);
    }

    public Optional<Tenant> readTenant(TenantName name) {
        if (name.value().startsWith(Tenant.userPrefix)) {
            return readUserTenant(name).map(Tenant.class::cast);
        }
        return readAthenzTenant(name).map(Tenant.class::cast);
    }

    public List<Tenant> readTenants() {
        return curator.getChildren(tenantRoot).stream()
                      .map(TenantName::from)
                      .map(this::readTenant)
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public void removeTenant(TenantName name) {
        curator.delete(tenantPath(name));
    }

    // -------------- Application ---------------------------------------------

    public void writeApplication(Application application) {
        try {
            curator.set(applicationPath(application.id()),
                        SlimeUtils.toJsonBytes(applicationSerializer.toSlime(application)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + application.id().toString(), e);
        }
    }

    public Optional<Application> readApplication(ApplicationId application) {
        return readSlime(applicationPath(application)).map(applicationSerializer::fromSlime);
    }

    public List<Application> readApplications() {
        return readApplications(ignored -> true);
    }

    public List<Application> readApplications(TenantName name) {
        return readApplications(application -> application.tenant().equals(name));
    }

    private List<Application> readApplications(Predicate<ApplicationId> applicationFilter) {
        return curator.getChildren(applicationRoot).stream()
                      .map(ApplicationId::fromSerializedForm)
                      .filter(applicationFilter)
                      .map(this::readApplication)
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public void removeApplication(ApplicationId application) {
        curator.delete(applicationPath(application));
    }

    // -------------- Provisioning (called by internal code) ------------------

    @SuppressWarnings("unused")
    public Optional<byte[]> readProvisionState(String provisionId) {
        return curator.getData(provisionStatePath(provisionId));
    }

    @SuppressWarnings("unused")
    public void writeProvisionState(String provisionId, byte[] data) {
        curator.set(provisionStatePath(provisionId), data);
    }

    @SuppressWarnings("unused")
    public List<String> readProvisionStateIds() {
        return curator.getChildren(provisionStatePath());
    }

    @SuppressWarnings("unused")
    public Optional<byte[]> readVespaServerPool() {
        return curator.getData(vespaServerPoolPath());
    }

    @SuppressWarnings("unused")
    public void writeVespaServerPool(byte[] data) {
        curator.set(vespaServerPoolPath(), data);
    }

    @SuppressWarnings("unused")
    public Optional<byte[]> readOpenStackServerPool() {
        return curator.getData(openStackServerPoolPath());
    }

    @SuppressWarnings("unused")
    public void writeOpenStackServerPool(byte[] data) {
        curator.set(openStackServerPoolPath(), data);
    }

    // -------------- Paths ---------------------------------------------------

    private Path lockPath(TenantName tenant) {
        Path lockPath = lockRoot
                .append(tenant.value());
        curator.create(lockPath);
        return lockPath;
    }

    private Path lockPath(ApplicationId application) {
        Path lockPath = lockRoot
                .append(application.tenant().value())
                .append(application.application().value())
                .append(application.instance().value());
        curator.create(lockPath);
        return lockPath;
    }

    private Path lockPath(String provisionId) {
        Path lockPath = lockRoot
                .append(provisionStatePath())
                .append(provisionId);
        curator.create(lockPath);
        return lockPath;
    }

    private static Path inactiveJobsPath() {
        return root.append("inactiveJobs");
    }

    private static Path jobQueuePath(DeploymentJobs.JobType jobType) {
        return root.append("jobQueues").append(jobType.name());
    }

    private static Path upgradesPerMinutePath() {
        return root.append("upgrader").append("upgradesPerMinute");
    }

    private static Path confidenceOverridesPath() {
        return root.append("upgrader").append("confidenceOverrides");
    }

    private static Path versionStatusPath() {
        return root.append("versionStatus");
    }

    private static Path provisionStatePath() {
        return root.append("provisioning").append("states");
    }

    private static Path provisionStatePath(String provisionId) {
        return provisionStatePath().append(provisionId);
    }

    private static Path vespaServerPoolPath() {
        return root.append("vespaServerPool");
    }

    private static Path openStackServerPoolPath() {
        return root.append("openStackServerPool");
    }

    private static Path tenantPath(TenantName name) {
        return tenantRoot.append(name.value());
    }

    private static Path applicationPath(ApplicationId application) {
        return applicationRoot.append(application.serializedForm());
    }

}
