// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.DnsChallenge;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;
import com.yahoo.vespa.hosted.controller.deployment.RetriggerEntry;
import com.yahoo.vespa.hosted.controller.deployment.RetriggerEntrySerializer;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import com.yahoo.vespa.hosted.controller.routing.ZoneRoutingPolicy;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccess;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
    private static final Duration deployLockTimeout = Duration.ofMinutes(30);
    private static final Duration defaultLockTimeout = Duration.ofMinutes(5);
    private static final Duration defaultTryLockTimeout = Duration.ofSeconds(1);

    private static final Path root = Path.fromString("/controller/v1");

    private static final Path lockRoot = root.append("locks");

    private static final Path tenantRoot = root.append("tenants");
    private static final Path applicationRoot = root.append("applications");
    private static final Path jobRoot = root.append("jobs");
    private static final Path controllerRoot = root.append("controllers");
    private static final Path routingPoliciesRoot = root.append("routingPolicies");
    private static final Path dnsChallengesRoot = root.append("dnsChallenges");
    private static final Path zoneRoutingPoliciesRoot = root.append("zoneRoutingPolicies");
    private static final Path endpointCertificateRoot = root.append("applicationCertificates");
    private static final Path archiveBucketsRoot = root.append("archiveBuckets");
    private static final Path changeRequestsRoot = root.append("changeRequests");
    private static final Path notificationsRoot = root.append("notifications");
    private static final Path supportAccessRoot = root.append("supportAccess");
    private static final Path mailVerificationRoot = root.append("mailVerification");

    private final NodeVersionSerializer nodeVersionSerializer = new NodeVersionSerializer();
    private final VersionStatusSerializer versionStatusSerializer = new VersionStatusSerializer(nodeVersionSerializer);
    private final ControllerVersionSerializer controllerVersionSerializer = new ControllerVersionSerializer();
    private final ConfidenceOverrideSerializer confidenceOverrideSerializer = new ConfidenceOverrideSerializer();
    private final TenantSerializer tenantSerializer = new TenantSerializer();
    private final OsVersionSerializer osVersionSerializer = new OsVersionSerializer();
    private final OsVersionTargetSerializer osVersionTargetSerializer = new OsVersionTargetSerializer(osVersionSerializer);
    private final OsVersionStatusSerializer osVersionStatusSerializer = new OsVersionStatusSerializer(osVersionSerializer, nodeVersionSerializer);
    private final RoutingPolicySerializer routingPolicySerializer = new RoutingPolicySerializer();
    private final ZoneRoutingPolicySerializer zoneRoutingPolicySerializer = new ZoneRoutingPolicySerializer(routingPolicySerializer);
    private final AuditLogSerializer auditLogSerializer = new AuditLogSerializer();
    private final NameServiceQueueSerializer nameServiceQueueSerializer = new NameServiceQueueSerializer();
    private final ApplicationSerializer applicationSerializer = new ApplicationSerializer();
    private final RunSerializer runSerializer = new RunSerializer();
    private final RetriggerEntrySerializer retriggerEntrySerializer = new RetriggerEntrySerializer();
    private final NotificationsSerializer notificationsSerializer = new NotificationsSerializer();
    private final DnsChallengeSerializer dnsChallengeSerializer = new DnsChallengeSerializer();

    private final Curator curator;
    private final Duration tryLockTimeout;

    // For each application id (path), store the ZK node version and its deserialised data - update when version changes.
    // This will grow to keep all applications in memory, but this should be OK
    private final Map<Path, Pair<Integer, Application>> cachedApplications = new ConcurrentHashMap<>();

    // For each job id (path), store the ZK node version and its deserialised data - update when version changes.
    private final Map<Path, Pair<Integer, NavigableMap<RunId, Run>>> cachedHistoricRuns = new ConcurrentHashMap<>();

    // Store the ZK node version and its deserialised data - update when version changes.
    private final AtomicReference<Pair<Integer, VersionStatus>> cachedVersionStatus = new AtomicReference<>();

    @Inject
    public CuratorDb(Curator curator) {
        this(curator, defaultTryLockTimeout);
    }

    CuratorDb(Curator curator, Duration tryLockTimeout) {
        this.curator = curator;
        this.tryLockTimeout = tryLockTimeout;
    }

    /** Returns all hostnames configured to be part of this ZooKeeper cluster */
    public List<String> cluster() {
        return Arrays.stream(curator.zooKeeperEnsembleConnectionSpec().split(","))
                     .filter(hostAndPort -> !hostAndPort.isEmpty())
                     .map(hostAndPort -> hostAndPort.split(":")[0])
                     .toList();
    }

    // -------------- Locks ---------------------------------------------------

    public Mutex lock(TenantName name) {
        return curator.lock(lockRoot.append("tenants").append(name.value()), defaultLockTimeout.multipliedBy(2));
    }

    public Mutex lock(TenantAndApplicationId id) {
        return curator.lock(lockRoot.append("applications").append(id.tenant().value() + ":" +
                                                                   id.application().value()),
                            defaultLockTimeout.multipliedBy(2));
    }

    public Mutex lockForDeployment(ApplicationId id, ZoneId zone) {
        return curator.lock(lockRoot.append("instances").append(id.serializedForm() + ":" + zone.environment().value() +
                                                                ":" + zone.region().value()),
                            deployLockTimeout);
    }

    public Mutex lock(ApplicationId id, JobType type) {
        return curator.lock(lockRoot.append("jobs").append(id.serializedForm() + ":" + type.jobName()),
                            defaultLockTimeout);
    }

    public Mutex lock(ApplicationId id, JobType type, Step step) throws TimeoutException {
        return tryLock(lockRoot.append("steps").append(id.serializedForm() + ":" + type.jobName() + ":" + step.name()));
    }

    public Mutex lockRotations() {
        return curator.lock(lockRoot.append("rotations"), defaultLockTimeout);
    }

    public Mutex lockConfidenceOverrides() {
        return curator.lock(lockRoot.append("confidenceOverrides"), defaultLockTimeout);
    }

    public Mutex lockMaintenanceJob(String jobName) {
        try {
            return tryLock(lockRoot.append("maintenanceJobLocks").append(jobName));
        } catch (TimeoutException e) {
            throw new UncheckedTimeoutException(e);
        }
    }

    public Mutex lockProvisionState(String provisionStateId) {
        return curator.lock(lockRoot.append("provisioning").append("states").append(provisionStateId), Duration.ofSeconds(1));
    }

    public Mutex lockOsVersions() {
        return curator.lock(lockRoot.append("osTargetVersion"), defaultLockTimeout);
    }

    public Mutex lockOsVersionStatus() {
        return curator.lock(lockRoot.append("osVersionStatus"), defaultLockTimeout);
    }

    public Mutex lockRoutingPolicies() {
        return curator.lock(lockRoot.append("routingPolicies"), defaultLockTimeout);
    }

    public Mutex lockAuditLog() {
        return curator.lock(lockRoot.append("auditLog"), defaultLockTimeout);
    }

    public Mutex lockNameServiceQueue() {
        return curator.lock(lockRoot.append("nameServiceQueue"), defaultLockTimeout);
    }

    public Mutex lockMeteringRefreshTime() throws TimeoutException {
        return tryLock(lockRoot.append("meteringRefreshTime"));
    }

    public Mutex lockArchiveBuckets(ZoneId zoneId) {
        return curator.lock(lockRoot.append("archiveBuckets").append(zoneId.value()), defaultLockTimeout);
    }

    public Mutex lockChangeRequests() {
        return curator.lock(lockRoot.append("changeRequests"), defaultLockTimeout);
    }

    public Mutex lockNotifications(TenantName tenantName) {
        return curator.lock(lockRoot.append("notifications").append(tenantName.value()), defaultLockTimeout);
    }

    public Mutex lockSupportAccess(DeploymentId deploymentId) {
        return curator.lock(lockRoot.append("supportAccess").append(deploymentId.dottedString()), defaultLockTimeout);
    }

    public Mutex lockDeploymentRetriggerQueue() {
        return curator.lock(lockRoot.append("deploymentRetriggerQueue"), defaultLockTimeout);
    }

    public Mutex lockPendingMailVerification(String verificationCode) {
        return curator.lock(lockRoot.append("pendingMailVerification").append(verificationCode), defaultLockTimeout);
    }

    // -------------- Helpers ------------------------------------------

    /** Try locking with a low timeout, meaning it is OK to fail lock acquisition.
     *
     * Useful for maintenance jobs, where there is no point in running the jobs back to back.
     */
    private Mutex tryLock(Path path) throws TimeoutException {
        try {
            return curator.lock(path, tryLockTimeout);
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

    public double readUpgradesPerMinute() {
        return read(upgradesPerMinutePath(), ByteBuffer::wrap).map(ByteBuffer::getDouble).orElse(0.125);
    }

    public void writeUpgradesPerMinute(double n) {
        curator.set(upgradesPerMinutePath(), ByteBuffer.allocate(Double.BYTES).putDouble(n).array());
    }

    public void writeVersionStatus(VersionStatus status) {
        curator.set(versionStatusPath(), asJson(versionStatusSerializer.toSlime(status)));
    }

    public VersionStatus readVersionStatus() {
        Path path = versionStatusPath();
        return curator.getStat(path)
                      .map(stat -> cachedVersionStatus.updateAndGet(old ->
                          old != null && old.getFirst() == stat.getVersion()
                                 ? old
                                 : new Pair<>(stat.getVersion(), read(path, bytes -> versionStatusSerializer.fromSlime(SlimeUtils.jsonToSlime(bytes))).get())).getSecond())
                      .orElseGet(VersionStatus::empty);
    }

    public void writeConfidenceOverrides(Map<Version, VespaVersion.Confidence> overrides) {
        curator.set(confidenceOverridesPath(), asJson(confidenceOverrideSerializer.toSlime(overrides)));
    }

    public Map<Version, VespaVersion.Confidence> readConfidenceOverrides() {
        return readSlime(confidenceOverridesPath()).map(confidenceOverrideSerializer::fromSlime)
                                                   .orElseGet(Collections::emptyMap);
    }

    public void writeControllerVersion(HostName hostname, ControllerVersion version) {
        curator.set(controllerPath(hostname.value()), asJson(controllerVersionSerializer.toSlime(version)));
    }

    public ControllerVersion readControllerVersion(HostName hostname) {
        return readSlime(controllerPath(hostname.value()))
                .map(controllerVersionSerializer::fromSlime)
                .orElse(ControllerVersion.CURRENT);
    }

    // Infrastructure upgrades

    public void writeOsVersionTargets(Set<OsVersionTarget> versions) {
        curator.set(osVersionTargetsPath(), asJson(osVersionTargetSerializer.toSlime(versions)));
    }

    public Set<OsVersionTarget> readOsVersionTargets() {
        return readSlime(osVersionTargetsPath()).map(osVersionTargetSerializer::fromSlime).orElseGet(Collections::emptySet);
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

    public Optional<Tenant> readTenant(TenantName name) {
        return readSlime(tenantPath(name)).map(tenantSerializer::tenantFrom);
    }

    public List<Tenant> readTenants() {
        return readTenantNames().stream()
                                .map(this::readTenant)
                                .flatMap(Optional::stream)
                                .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<TenantName> readTenantNames() {
        return curator.getChildren(tenantRoot).stream()
                      .map(TenantName::from)
                      .toList();
    }

    public void removeTenant(TenantName name) {
        curator.delete(tenantPath(name));
    }

    // -------------- Applications ---------------------------------------------

    public void writeApplication(Application application) {
        curator.set(applicationPath(application.id()), asJson(applicationSerializer.toSlime(application)));
    }

    public Optional<Application> readApplication(TenantAndApplicationId application) {
        Path path = applicationPath(application);
        return curator.getStat(path)
                      .map(stat -> cachedApplications.compute(path, (__, old) ->
                              old != null && old.getFirst() == stat.getVersion()
                              ? old
                              : new Pair<>(stat.getVersion(), read(path, bytes -> applicationSerializer.fromSlime(bytes)).get())).getSecond());
    }

    public List<Application> readApplications(boolean canFail) {
        return readApplications(ignored -> true, canFail);
    }

    public List<Application> readApplications(TenantName name) {
        return readApplications(application -> application.tenant().equals(name), false);
    }

    private List<Application> readApplications(Predicate<TenantAndApplicationId> applicationFilter, boolean canFail) {
        var applicationIds = readApplicationIds();
        var applications = new ArrayList<Application>(applicationIds.size());
        for (var id : applicationIds) {
            if (!applicationFilter.test(id)) continue;
            try {
                readApplication(id).ifPresent(applications::add);
            } catch (Exception e) {
                if (canFail) {
                    log.log(Level.SEVERE, "Failed to read application '" + id + "', this must be fixed through " +
                                            "manual intervention", e);
                } else {
                    throw e;
                }
            }
        }
        return Collections.unmodifiableList(applications);
    }

    public List<TenantAndApplicationId> readApplicationIds() {
        return curator.getChildren(applicationRoot).stream()
                      .map(TenantAndApplicationId::fromSerialized)
                      .sorted()
                      .toList();
    }

    public void removeApplication(TenantAndApplicationId id) {
        curator.delete(applicationPath(id));
    }

    // -------------- Job Runs ------------------------------------------------

    public void writeLastRun(Run run) {
        curator.set(lastRunPath(run.id().application(), run.id().type()), asJson(runSerializer.toSlime(run)));
    }

    public void writeHistoricRuns(ApplicationId id, JobType type, Iterable<Run> runs) {
        Path path = runsPath(id, type);
        curator.set(path, asJson(runSerializer.toSlime(runs)));
    }

    public Optional<Run> readLastRun(ApplicationId id, JobType type) {
        return readSlime(lastRunPath(id, type)).map(runSerializer::runFromSlime);
    }

    public NavigableMap<RunId, Run> readHistoricRuns(ApplicationId id, JobType type) {
        Path path = runsPath(id, type);
        return curator.getStat(path)
                      .map(stat -> cachedHistoricRuns.compute(path, (__, old) ->
                              old != null && old.getFirst() == stat.getVersion()
                              ? old
                              : new Pair<>(stat.getVersion(), runSerializer.runsFromSlime(readSlime(path).get()))).getSecond())
                      .orElseGet(Collections::emptyNavigableMap);
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
                      .toList();
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

    // -------------- Audit log -----------------------------------------------

    public AuditLog readAuditLog() {
        return readSlime(auditLogPath()).map(auditLogSerializer::fromSlime)
                                        .orElse(AuditLog.empty);
    }

    public void writeAuditLog(AuditLog log) {
        curator.set(auditLogPath(), asJson(auditLogSerializer.toSlime(log)));
    }


    // -------------- Name service log ----------------------------------------

    public NameServiceQueue readNameServiceQueue() {
        return readSlime(nameServiceQueuePath()).map(nameServiceQueueSerializer::fromSlime)
                                                .orElse(NameServiceQueue.EMPTY);
    }

    public void writeNameServiceQueue(NameServiceQueue queue) {
        curator.set(nameServiceQueuePath(), asJson(nameServiceQueueSerializer.toSlime(queue)));
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

    // -------------- Routing policies ----------------------------------------

    public void writeRoutingPolicies(ApplicationId application, List<RoutingPolicy> policies) {
        for (var policy : policies) {
            if (!policy.id().owner().equals(application)) {
                throw new IllegalArgumentException(policy.id() + " does not belong to the application being written: " +
                                                   application.toShortString());
            }
        }
        curator.set(routingPolicyPath(application), asJson(routingPolicySerializer.toSlime(policies)));
    }

    public Map<ApplicationId, List<RoutingPolicy>> readRoutingPolicies() {
        return readRoutingPolicies((instance) -> true);
    }

    public Map<ApplicationId, List<RoutingPolicy>> readRoutingPolicies(Predicate<ApplicationId> filter) {
        return curator.getChildren(routingPoliciesRoot).stream()
                      .map(ApplicationId::fromSerializedForm)
                      .filter(filter)
                      .collect(Collectors.toUnmodifiableMap(Function.identity(),
                                                            this::readRoutingPolicies));
    }

    public List<RoutingPolicy> readRoutingPolicies(ApplicationId application) {
        return readSlime(routingPolicyPath(application)).map(slime -> routingPolicySerializer.fromSlime(application, slime))
                                                        .orElseGet(List::of);
    }

    public void writeZoneRoutingPolicy(ZoneRoutingPolicy policy) {
        curator.set(zoneRoutingPolicyPath(policy.zone()), asJson(zoneRoutingPolicySerializer.toSlime(policy)));
    }

    public ZoneRoutingPolicy readZoneRoutingPolicy(ZoneId zone) {
        return readSlime(zoneRoutingPolicyPath(zone)).map(data -> zoneRoutingPolicySerializer.fromSlime(zone, data))
                                                     .orElseGet(() -> new ZoneRoutingPolicy(zone, RoutingStatus.DEFAULT));
    }

    public void writeDnsChallenge(DnsChallenge challenge) {
        curator.set(dnsChallengePath(challenge.clusterId()), dnsChallengeSerializer.toJson(challenge));
    }

    public void deleteDnsChallenge(ClusterId id) {
        curator.delete(dnsChallengePath(id));
    }

    public List<DnsChallenge> readDnsChallenges(DeploymentId id) {
        return curator.getChildren(dnsChallengePath(id)).stream()
                      .map(cluster -> readDnsChallenge(new ClusterId(id, ClusterSpec.Id.from(cluster))))
                      .toList();
    }

    private DnsChallenge readDnsChallenge(ClusterId clusterId) {
        return curator.getData(dnsChallengePath(clusterId))
                      .map(bytes -> dnsChallengeSerializer.fromJson(bytes, clusterId))
                      .orElseThrow(() -> new IllegalArgumentException("no DNS challenge for " + clusterId));
    }

    private static Path dnsChallengePath(DeploymentId id) {
        return dnsChallengesRoot.append(id.applicationId().serializedForm())
                                .append(id.zoneId().value());
    }

    private static Path dnsChallengePath(ClusterId id) {
        return dnsChallengePath(id.deploymentId()).append(id.clusterId().value());
    }

    // -------------- Application endpoint certificates ----------------------------

    public void writeEndpointCertificateMetadata(ApplicationId applicationId, EndpointCertificateMetadata endpointCertificateMetadata) {
        curator.set(endpointCertificatePath(applicationId), asJson(EndpointCertificateMetadataSerializer.toSlime(endpointCertificateMetadata)));
    }

    public void deleteEndpointCertificateMetadata(ApplicationId applicationId) {
        curator.delete(endpointCertificatePath(applicationId));
    }

    public Optional<EndpointCertificateMetadata> readEndpointCertificateMetadata(ApplicationId applicationId) {
        return curator.getData(endpointCertificatePath(applicationId)).map(String::new).map(EndpointCertificateMetadataSerializer::fromJsonString);
    }

    public Map<ApplicationId, EndpointCertificateMetadata> readAllEndpointCertificateMetadata() {
        Map<ApplicationId, EndpointCertificateMetadata> allEndpointCertificateMetadata = new HashMap<>();

        for (String appIdString : curator.getChildren(endpointCertificateRoot)) {
            ApplicationId applicationId = ApplicationId.fromSerializedForm(appIdString);
            Optional<EndpointCertificateMetadata> endpointCertificateMetadata = readEndpointCertificateMetadata(applicationId);
            allEndpointCertificateMetadata.put(applicationId, endpointCertificateMetadata.orElseThrow());
        }
        return allEndpointCertificateMetadata;
    }

    // -------------- Metering view refresh times ----------------------------

    public void writeMeteringRefreshTime(long timestamp) {
        curator.set(meteringRefreshPath(), Long.toString(timestamp).getBytes());
    }

    public long readMeteringRefreshTime() {
        return curator.getData(meteringRefreshPath())
                      .map(String::new).map(Long::parseLong)
                      .orElse(0L);
    }

    // -------------- Archive buckets -----------------------------------------

    public ArchiveBuckets readArchiveBuckets(ZoneId zoneId) {
        return readSlime(archiveBucketsPath(zoneId)).map(ArchiveBucketsSerializer::fromSlime)
                .orElse(ArchiveBuckets.EMPTY);
    }

    public void writeArchiveBuckets(ZoneId zoneid, ArchiveBuckets archiveBuckets) {
        curator.set(archiveBucketsPath(zoneid), asJson(ArchiveBucketsSerializer.toSlime(archiveBuckets)));
    }

    // -------------- VCMRs ---------------------------------------------------

    public Optional<VespaChangeRequest> readChangeRequest(String changeRequestId) {
        return readSlime(changeRequestPath(changeRequestId)).map(ChangeRequestSerializer::fromSlime);
    }

    public List<VespaChangeRequest> readChangeRequests() {
        return curator.getChildren(changeRequestsRoot)
                .stream()
                .map(this::readChangeRequest)
                .flatMap(Optional::stream)
                .toList();
    }

    public void writeChangeRequest(VespaChangeRequest changeRequest) {
        curator.set(changeRequestPath(changeRequest.getId()), asJson(ChangeRequestSerializer.toSlime(changeRequest)));
    }

    public void deleteChangeRequest(VespaChangeRequest changeRequest) {
        curator.delete(changeRequestPath(changeRequest.getId()));
    }

    // -------------- Notifications -------------------------------------------

    public List<Notification> readNotifications(TenantName tenantName) {
        return readSlime(notificationsPath(tenantName))
                .map(slime -> notificationsSerializer.fromSlime(tenantName, slime)).orElseGet(List::of);
    }


    public List<TenantName> listTenantsWithNotifications() {
        return curator.getChildren(notificationsRoot).stream()
                      .map(TenantName::from)
                      .toList();
    }

    public void writeNotifications(TenantName tenantName, List<Notification> notifications) {
        curator.set(notificationsPath(tenantName), asJson(notificationsSerializer.toSlime(notifications)));
    }

    public void deleteNotifications(TenantName tenantName) {
        curator.delete(notificationsPath(tenantName));
    }

    // -------------- Endpoint Support Access ---------------------------------

    public SupportAccess readSupportAccess(DeploymentId deploymentId) {
        return readSlime(supportAccessPath(deploymentId)).map(SupportAccessSerializer::fromSlime).orElse(SupportAccess.DISALLOWED_NO_HISTORY);
    }

    public void writeSupportAccess(DeploymentId deploymentId, SupportAccess supportAccess) {
        curator.set(supportAccessPath(deploymentId), asJson(SupportAccessSerializer.toSlime(supportAccess)));
    }

    // -------------- Job Retrigger entries -----------------------------------

    public List<RetriggerEntry> readRetriggerEntries() {
        return readSlime(deploymentRetriggerPath()).map(retriggerEntrySerializer::fromSlime).orElseGet(List::of);
    }

    public void writeRetriggerEntries(List<RetriggerEntry> retriggerEntries) {
        curator.set(deploymentRetriggerPath(), asJson(retriggerEntrySerializer.toSlime(retriggerEntries)));
    }

    // -------------- Pending mail verification -------------------------------

    public Optional<PendingMailVerification> getPendingMailVerification(String verificationCode) {
        return readSlime(mailVerificationPath(verificationCode)).map(MailVerificationSerializer::fromSlime);
    }

    public List<PendingMailVerification> listPendingMailVerifications() {
        return curator.getChildren(mailVerificationRoot)
                .stream()
                .map(this::getPendingMailVerification)
                .flatMap(Optional::stream)
                .toList();
    }

    public void writePendingMailVerification(PendingMailVerification pendingMailVerification) {
        curator.set(mailVerificationPath(pendingMailVerification.getVerificationCode()), asJson(MailVerificationSerializer.toSlime(pendingMailVerification)));
    }

    public void deletePendingMailVerification(PendingMailVerification pendingMailVerification) {
        curator.delete(mailVerificationPath(pendingMailVerification.getVerificationCode()));
    }

    // -------------- Paths ---------------------------------------------------

    private static Path upgradesPerMinutePath() {
        return root.append("upgrader").append("upgradesPerMinute");
    }

    private static Path confidenceOverridesPath() {
        return root.append("upgrader").append("confidenceOverrides");
    }

    private static Path osVersionTargetsPath() {
        return root.append("osUpgrader").append("targetVersion");
    }

    private static Path osVersionStatusPath() {
        return root.append("osVersionStatus");
    }

    private static Path versionStatusPath() {
        return root.append("versionStatus");
    }

    private static Path routingPolicyPath(ApplicationId application) {
        return routingPoliciesRoot.append(application.serializedForm());
    }

    private static Path zoneRoutingPolicyPath(ZoneId zone) { return zoneRoutingPoliciesRoot.append(zone.value()); }

    private static Path nameServiceQueuePath() {
        return root.append("nameServiceQueue");
    }

    private static Path auditLogPath() {
        return root.append("auditLog");
    }

    private static Path provisionStatePath() {
        return root.append("provisioning").append("states");
    }

    private static Path provisionStatePath(String provisionId) {
        return provisionStatePath().append(provisionId);
    }

    private static Path tenantPath(TenantName name) {
        return tenantRoot.append(name.value());
    }

    private static Path applicationPath(TenantAndApplicationId id) {
        return applicationRoot.append(id.serialized());
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

    private static Path endpointCertificatePath(ApplicationId id) {
        return endpointCertificateRoot.append(id.serializedForm());
    }

    private static Path meteringRefreshPath() {
        return root.append("meteringRefreshTime");
    }

    private static Path archiveBucketsPath(ZoneId zoneId) {
        return archiveBucketsRoot.append(zoneId.value());
    }

    private static Path changeRequestPath(String id) {
        return changeRequestsRoot.append(id);
    }

    private static Path notificationsPath(TenantName tenantName) {
        return notificationsRoot.append(tenantName.value());
    }

    private static Path supportAccessPath(DeploymentId deploymentId) {
        return supportAccessRoot.append(deploymentId.dottedString());
    }

    private static Path deploymentRetriggerPath() {
        return root.append("deploymentRetriggerQueue");
    }

    private static Path mailVerificationPath(String verificationCode) {
        return mailVerificationRoot.append(verificationCode);
    }

}
