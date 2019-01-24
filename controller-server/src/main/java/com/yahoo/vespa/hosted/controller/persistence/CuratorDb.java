// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.loadbalancer.LoadBalancerName;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;

/**
 * Curator backed database for storing the persistence state of controllers. This maps controller specific operations
 * to general curator operations.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class CuratorDb {

    private static final Logger log = Logger.getLogger(CuratorDb.class.getName());
    private static final Duration defaultLockTimeout = Duration.ofMinutes(5);
    private static final Duration defaultTryLockTimeout = Duration.ofSeconds(1);

    private static final Path root = Path.fromString("/controller/v1");
    private static final Path lockRoot = root.append("locks");
    private static final Path tenantRoot = root.append("tenants");
    private static final Path applicationRoot = root.append("applications");
    private static final Path jobRoot = root.append("jobs");
    private static final Path controllerRoot = root.append("controllers");

    private final StringSetSerializer stringSetSerializer = new StringSetSerializer();
    private final VersionStatusSerializer versionStatusSerializer = new VersionStatusSerializer();
    private final VersionSerializer versionSerializer = new VersionSerializer();
    private final ConfidenceOverrideSerializer confidenceOverrideSerializer = new ConfidenceOverrideSerializer();
    private final TenantSerializer tenantSerializer = new TenantSerializer();
    private final ApplicationSerializer applicationSerializer = new ApplicationSerializer();
    private final RunSerializer runSerializer = new RunSerializer();
    private final OsVersionSerializer osVersionSerializer = new OsVersionSerializer();
    private final OsVersionStatusSerializer osVersionStatusSerializer = new OsVersionStatusSerializer(osVersionSerializer);
    private final LoadBalancerNameSerializer loadBalancerNameSerializer = new LoadBalancerNameSerializer();

    private final Curator curator;
    private final Duration tryLockTimeout;

    /**
     * All keys, to allow reentrancy.
     * This will grow forever, but this should be too slow to be a problem.
     */
    private final ConcurrentHashMap<Path, Lock> locks = new ConcurrentHashMap<>();

    @Inject
    public CuratorDb(Curator curator) {
        this(curator, defaultTryLockTimeout);
    }

    CuratorDb(Curator curator, Duration tryLockTimeout) {
        this.curator = curator;
        this.tryLockTimeout = tryLockTimeout;
    }

    /** Returns all hosts configured to be part of this ZooKeeper cluster */
    public List<HostName> cluster() {
        return Arrays.stream(curator.zooKeeperEnsembleConnectionSpec().split(","))
                     .filter(hostAndPort -> !hostAndPort.isEmpty())
                     .map(hostAndPort -> hostAndPort.split(":")[0])
                     .map(HostName::from)
                     .collect(Collectors.toList());
    }

    // -------------- Locks ---------------------------------------------------

    /** Create a reentrant lock */
    private Lock lock(Path path, Duration timeout) {
        Lock lock = locks.computeIfAbsent(path, (pathArg) -> new Lock(pathArg.getAbsolute(), curator));
        lock.acquire(timeout);
        return lock;
    }

    public Lock lock(TenantName name) {
        return lock(lockPath(name), defaultLockTimeout.multipliedBy(2));
    }

    public Lock lock(ApplicationId id) {
        return lock(lockPath(id), defaultLockTimeout.multipliedBy(2));
    }

    public Lock lock(ApplicationId id, JobType type) {
        return lock(lockPath(id, type), defaultLockTimeout);
    }

    public Lock lock(ApplicationId id, JobType type, Step step) throws TimeoutException {
        return tryLock(lockPath(id, type, step));
    }

    public Lock lockRotations() {
        return lock(lockRoot.append("rotations"), defaultLockTimeout);
    }

    public Lock lockConfidenceOverrides() {
        return lock(lockRoot.append("confidenceOverrides"), defaultLockTimeout);
    }

    public Lock lockInactiveJobs() {
        return lock(lockRoot.append("inactiveJobsLock"), defaultLockTimeout);
    }

    public Lock lockMaintenanceJob(String jobName) throws TimeoutException {
        return tryLock(lockRoot.append("maintenanceJobLocks").append(jobName));
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

    public Lock lockOsVersions() {
        return lock(lockRoot.append("osTargetVersion"), defaultLockTimeout);
    }

    public Lock lockOsVersionStatus() {
        return lock(lockRoot.append("osVersionStatus"), defaultLockTimeout);
    }

    public Lock lockLoadBalancerNames() {
        return lock(lockRoot.append("loadBalancerNames"), defaultLockTimeout);
    }
    // -------------- Helpers ------------------------------------------

    /** Try locking with a low timeout, meaning it is OK to fail lock acquisition.
     *
     * Useful for maintenance jobs, where there is no point in running the jobs back to back.
     */
    private Lock tryLock(Path path) throws TimeoutException {
        try {
            return lock(path, tryLockTimeout);
        }
        catch (UncheckedTimeoutException e) {
            throw new TimeoutException(e.getMessage());
        }
    }

    private <T> Optional<T> read(Path path, Function<byte[], T> mapper) {
        return curator.getData(path).filter(data -> data.length > 0).map(mapper);
    }

    private Optional<Slime> readSlime(Path path) {
        return read(path, SlimeUtils::jsonToSlime);
    }

    private static byte[] asJson(Slime slime) {
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        return read(upgradesPerMinutePath(), ByteBuffer::wrap).map(ByteBuffer::getDouble).orElse(0.125);
    }

    public void writeUpgradesPerMinute(double n) {
        curator.set(upgradesPerMinutePath(), ByteBuffer.allocate(Double.BYTES).putDouble(n).array());
    }

    public Optional<Integer> readTargetMajorVersion() {
        return read(targetMajorVersionPath(), ByteBuffer::wrap).map(ByteBuffer::getInt);
    }

    public void writeTargetMajorVersion(Optional<Integer> targetMajorVersion) {
        if (targetMajorVersion.isPresent())
            curator.set(targetMajorVersionPath(), ByteBuffer.allocate(Integer.BYTES).putInt(targetMajorVersion.get()).array());
        else
            curator.delete(targetMajorVersionPath());
    }

    public void writeVersionStatus(VersionStatus status) {
        curator.set(versionStatusPath(), asJson(versionStatusSerializer.toSlime(status)));
    }

    public VersionStatus readVersionStatus() {
        return readSlime(versionStatusPath()).map(versionStatusSerializer::fromSlime).orElseGet(VersionStatus::empty);
    }

    public void writeConfidenceOverrides(Map<Version, VespaVersion.Confidence> overrides) {
        curator.set(confidenceOverridesPath(), asJson(confidenceOverrideSerializer.toSlime(overrides)));
    }

    public Map<Version, VespaVersion.Confidence> readConfidenceOverrides() {
        return readSlime(confidenceOverridesPath()).map(confidenceOverrideSerializer::fromSlime)
                                                   .orElseGet(Collections::emptyMap);
    }

    public void writeControllerVersion(HostName hostname, Version version) {
        curator.set(controllerPath(hostname.value()), asJson(versionSerializer.toSlime(version)));
    }

    public Version readControllerVersion(HostName hostname) {
        return readSlime(controllerPath(hostname.value()))
                .map(versionSerializer::fromSlime)
                .orElse(Vtag.currentVersion);
    }

    // Infrastructure upgrades

    public void writeOsVersions(Set<OsVersion> versions) {
        curator.set(osTargetVersionPath(), asJson(osVersionSerializer.toSlime(versions)));
    }

    public Set<OsVersion> readOsVersions() {
        return readSlime(osTargetVersionPath()).map(osVersionSerializer::fromSlime).orElseGet(Collections::emptySet);
    }

    public void writeOsVersionStatus(OsVersionStatus status) {
        curator.set(osVersionStatusPath(), asJson(osVersionStatusSerializer.toSlime(status)));
    }

    public OsVersionStatus readOsVersionStatus() {
        return readSlime(osVersionStatusPath()).map(osVersionStatusSerializer::fromSlime).orElse(OsVersionStatus.empty);
    }

    // -------------- Tenant --------------------------------------------------

    public void writeTenant(Tenant tenant) {
        curator.set(tenantPath(tenant.name()), asJson(tenantSerializer.toSlime(tenant)));
    }

    public Optional<UserTenant> readUserTenant(TenantName name) {
        return readSlime(tenantPath(name)).map(tenantSerializer::userTenantFrom);
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
        return readTenantNames().stream()
                                .map(this::readTenant)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<TenantName> readTenantNames() {
        return curator.getChildren(tenantRoot).stream()
                      .map(TenantName::from)
                      .collect(Collectors.toList());
    }

    public void removeTenant(TenantName name) {
        curator.delete(tenantPath(name));
    }

    // -------------- Application ---------------------------------------------

    public void writeApplication(Application application) {
        curator.set(applicationPath(application.id()), asJson(applicationSerializer.toSlime(application)));
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
                      .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public void removeApplication(ApplicationId application) {
        curator.delete(applicationPath(application));
    }

    // -------------- Job Runs ------------------------------------------------

    public void writeLastRun(Run run) {
        curator.set(lastRunPath(run.id().application(), run.id().type()), asJson(runSerializer.toSlime(run)));
    }

    public void writeHistoricRuns(ApplicationId id, JobType type, Iterable<Run> runs) {
        curator.set(runsPath(id, type), asJson(runSerializer.toSlime(runs)));
    }

    public Optional<Run> readLastRun(ApplicationId id, JobType type) {
        return readSlime(lastRunPath(id, type)).map(runSerializer::runFromSlime);
    }

    public SortedMap<RunId, Run> readHistoricRuns(ApplicationId id, JobType type) {
        return readSlime(runsPath(id, type)).map(runSerializer::runsFromSlime).orElse(new TreeMap<>(comparing(RunId::number)));
    }

    public void deleteRunData(ApplicationId id, JobType type) {
        curator.delete(runsPath(id, type));
        curator.delete(lastRunPath(id, type));
    }

    public void deleteRunData(ApplicationId id) {
        curator.delete(jobRoot.append(id.serializedForm()));
    }

    public List<ApplicationId> applicationsWithJobs() {
        return curator.getChildren(jobRoot).stream()
                      .map(ApplicationId::fromSerializedForm)
                      .collect(Collectors.toList());
    }


    public Optional<byte[]> readLog(ApplicationId id, JobType type, long chunkId) {
        return curator.getData(logPath(id, type, chunkId));
    }

    public void writeLog(ApplicationId id, JobType type, long chunkId, byte[] log) {
        curator.set(logPath(id, type, chunkId), log);
    }

    public void deleteLog(ApplicationId id, JobType type) {
        curator.delete(runsPath(id, type).append("logs"));
    }

    public Optional<Long> readLastLogEntryId(ApplicationId id, JobType type) {
        return curator.getData(lastLogPath(id, type))
                      .map(String::new).map(Long::parseLong);
    }

    public void writeLastLogEntryId(ApplicationId id, JobType type, long lastId) {
        curator.set(lastLogPath(id, type), Long.toString(lastId).getBytes());
    }

    public LongStream getLogChunkIds(ApplicationId id, JobType type) {
        return curator.getChildren(runsPath(id, type).append("logs")).stream()
                      .mapToLong(Long::parseLong)
                      .sorted();
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

    // -------------- Load balancer names -------------------------------------

    public void writeLoadBalancerNames(Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames) {
        curator.set(loadBalancerNamePath(), asJson(loadBalancerNameSerializer.toSlime(loadBalancerNames)));
    }

    public Map<ApplicationId, List<LoadBalancerName>> readLoadBalancerNames() {
        return readSlime(loadBalancerNamePath()).map(loadBalancerNameSerializer::fromSlime).orElseGet(Collections::emptyMap);
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

    private Path lockPath(ApplicationId application, JobType type) {
        Path lockPath = lockRoot
                .append(application.tenant().value())
                .append(application.application().value())
                .append(application.instance().value())
                .append(type.jobName());
        curator.create(lockPath);
        return lockPath;
    }

    private Path lockPath(ApplicationId application, JobType type, Step step) {
        Path lockPath = lockRoot
                .append(application.tenant().value())
                .append(application.application().value())
                .append(application.instance().value())
                .append(type.jobName())
                .append(step.name());
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

    private static Path upgradesPerMinutePath() {
        return root.append("upgrader").append("upgradesPerMinute");
    }

    private static Path targetMajorVersionPath() {
        return root.append("upgrader").append("targetMajorVersion");
    }

    private static Path confidenceOverridesPath() {
        return root.append("upgrader").append("confidenceOverrides");
    }

    private static Path osTargetVersionPath() {
        return root.append("osUpgrader").append("targetVersion");
    }

    private static Path osVersionStatusPath() {
        return root.append("osVersionStatus");
    }

    private static Path versionStatusPath() {
        return root.append("versionStatus");
    }

    private static Path loadBalancerNamePath() {
        return root.append("loadBalancerNames");
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

    private static Path runsPath(ApplicationId id, JobType type) {
        return jobRoot.append(id.serializedForm()).append(type.jobName());
    }

    private static Path lastRunPath(ApplicationId id, JobType type) {
        return runsPath(id, type).append("last");
    }

    private static Path logPath(ApplicationId id, JobType type, long first) {
        return runsPath(id, type).append("logs").append(Long.toString(first));
    }

    private static Path lastLogPath(ApplicationId id, JobType type) {
        return runsPath(id, type).append("logs");
    }

    private static Path controllerPath(String hostname) {
        return controllerRoot.append(hostname);
    }

}
