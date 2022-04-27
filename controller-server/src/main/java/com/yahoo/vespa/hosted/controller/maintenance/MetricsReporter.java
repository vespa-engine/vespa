// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This calculates and reports system-wide metrics based on data from a {@link Controller}.
 *
 * @author mortent
 * @author mpolden
 */
public class MetricsReporter extends ControllerMaintainer {

    public static final String TENANT_METRIC = "billing.tenants";
    public static final String DEPLOYMENT_FAIL_METRIC = "deployment.failurePercentage";
    public static final String DEPLOYMENT_AVERAGE_DURATION = "deployment.averageDuration";
    public static final String DEPLOYMENT_FAILING_UPGRADES = "deployment.failingUpgrades";
    public static final String DEPLOYMENT_BUILD_AGE_SECONDS = "deployment.buildAgeSeconds";
    public static final String DEPLOYMENT_WARNINGS = "deployment.warnings";
    public static final String DEPLOYMENT_OVERDUE_UPGRADE = "deployment.overdueUpgradeSeconds";
    public static final String OS_CHANGE_DURATION = "deployment.osChangeDuration";
    public static final String PLATFORM_CHANGE_DURATION = "deployment.platformChangeDuration";
    public static final String OS_NODE_COUNT = "deployment.nodeCountByOsVersion";
    public static final String PLATFORM_NODE_COUNT = "deployment.nodeCountByPlatformVersion";
    public static final String BROKEN_SYSTEM_VERSION = "deployment.brokenSystemVersion";
    public static final String REMAINING_ROTATIONS = "remaining_rotations";
    public static final String NAME_SERVICE_REQUESTS_QUEUED = "dns.queuedRequests";
    public static final String OPERATION_PREFIX = "operation.";
    public static final String ZMS_QUOTA_USAGE = "zms.quota.usage";

    private final Metric metric;
    private final Clock clock;
    private final ZmsClient zmsClient;

    // Keep track of reported node counts for each version
    private final ConcurrentHashMap<NodeCountKey, Long> nodeCounts = new ConcurrentHashMap<>();

    public MetricsReporter(Controller controller, Metric metric, ZmsClient zmsClient) {
        super(controller, Duration.ofMinutes(1)); // use fixed rate for metrics
        this.metric = metric;
        this.clock = controller.clock();
        this.zmsClient = zmsClient;
    }

    @Override
    public double maintain() {
        reportDeploymentMetrics();
        reportRemainingRotations();
        reportQueuedNameServiceRequests();
        VersionStatus versionStatus = controller().readVersionStatus();
        reportInfrastructureUpgradeMetrics(versionStatus);
        reportAuditLog();
        reportBrokenSystemVersion(versionStatus);
        reportTenantMetrics();
        reportZmsQuotaMetrics();
        return 1.0;
    }

    private void reportBrokenSystemVersion(VersionStatus versionStatus) {
        Version systemVersion = controller().systemVersion(versionStatus);
        VespaVersion.Confidence confidence = versionStatus.version(systemVersion).confidence();
        int isBroken = confidence == VespaVersion.Confidence.broken ? 1 : 0;
        metric.set(BROKEN_SYSTEM_VERSION, isBroken, metric.createContext(Map.of()));
    }

    private void reportAuditLog() {
        AuditLog log = controller().auditLogger().readLog();
        HashMap<String, HashMap<String, Integer>> metricCounts = new HashMap<>();

        for (AuditLog.Entry entry : log.entries()) {
            String[] resource = entry.resource().split("/");
            if((resource.length > 1) && (resource[1] != null)) {
                String api = resource[1];
                String operationMetric = OPERATION_PREFIX + api;
                HashMap<String, Integer> dimension = metricCounts.get(operationMetric);
                if (dimension != null) {
                    Integer count = dimension.get(entry.principal());
                    if (count != null) {
                        dimension.replace(entry.principal(), ++count);
                    } else {
                        dimension.put(entry.principal(), 1);
                    }

                } else {
                    dimension = new HashMap<>();
                    dimension.put(entry.principal(),1);
                    metricCounts.put(operationMetric, dimension);
                }
            }
        }
        for (String operationMetric : metricCounts.keySet()) {
            for (String userDimension : metricCounts.get(operationMetric).keySet()) {
                metric.set(operationMetric, (metricCounts.get(operationMetric)).get(userDimension), metric.createContext(Map.of("operator", userDimension)));
            }
        }
    }

    private void reportInfrastructureUpgradeMetrics(VersionStatus versionStatus) {
        Map<NodeVersion, Duration> osChangeDurations = osChangeDurations();
        Map<NodeVersion, Duration> platformChangeDurations = platformChangeDurations(versionStatus);
        reportChangeDurations(osChangeDurations, OS_CHANGE_DURATION);
        reportChangeDurations(platformChangeDurations, PLATFORM_CHANGE_DURATION);
        reportNodeCount(osChangeDurations.keySet(), OS_NODE_COUNT);
        reportNodeCount(platformChangeDurations.keySet(), PLATFORM_NODE_COUNT);
    }

    private void reportRemainingRotations() {
        try (RotationLock lock = controller().routing().rotations().lock()) {
            int availableRotations = controller().routing().rotations().availableRotations(lock).size();
            metric.set(REMAINING_ROTATIONS, availableRotations, metric.createContext(Map.of()));
        }
    }

    private void reportDeploymentMetrics() {
        ApplicationList applications = ApplicationList.from(controller().applications().readable())
                                                      .withProductionDeployment();
        DeploymentStatusList deployments = controller().jobController().deploymentStatuses(applications);

        metric.set(DEPLOYMENT_FAIL_METRIC, deploymentFailRatio(deployments) * 100, metric.createContext(Map.of()));

        averageDeploymentDurations(deployments, clock.instant()).forEach((instance, duration) -> {
            metric.set(DEPLOYMENT_AVERAGE_DURATION, duration.toSeconds(), metric.createContext(dimensions(instance)));
        });

        deploymentsFailingUpgrade(deployments).forEach((instance, failingJobs) -> {
            metric.set(DEPLOYMENT_FAILING_UPGRADES, failingJobs, metric.createContext(dimensions(instance)));
        });

        deploymentWarnings(deployments).forEach((instance, warnings) -> {
            metric.set(DEPLOYMENT_WARNINGS, warnings, metric.createContext(dimensions(instance)));
        });

        overdueUpgradeDurationByInstance(deployments).forEach((instance, overduePeriod) -> {
            metric.set(DEPLOYMENT_OVERDUE_UPGRADE, overduePeriod.toSeconds(), metric.createContext(dimensions(instance)));
        });

        for (Application application : applications.asList())
            application.revisions().last()
                       .flatMap(ApplicationVersion::buildTime)
                       .ifPresent(buildTime -> metric.set(DEPLOYMENT_BUILD_AGE_SECONDS,
                                                          controller().clock().instant().getEpochSecond() - buildTime.getEpochSecond(),
                                                          metric.createContext(dimensions(application.id().defaultInstance()))));
    }

    private Map<ApplicationId, Duration> overdueUpgradeDurationByInstance(DeploymentStatusList deployments) {
        Instant now = clock.instant();
        Map<ApplicationId, Duration> overdueUpgrades = new HashMap<>();
        for (var deploymentStatus : deployments) {
            for (var kv : deploymentStatus.instanceJobs().entrySet()) {
                ApplicationId instance = kv.getKey();
                JobList jobs = kv.getValue();
                boolean upgradeRunning = !jobs.production().upgrading().isEmpty();
                DeploymentInstanceSpec instanceSpec = deploymentStatus.application().deploymentSpec().requireInstance(instance.instance());
                Duration overdueDuration = upgradeRunning ? overdueUpgradeDuration(now, instanceSpec) : Duration.ZERO;
                overdueUpgrades.put(instance, overdueDuration);
            }
        }
        return Collections.unmodifiableMap(overdueUpgrades);
    }

    /** Returns how long an upgrade has been running inside a block window */
    static Duration overdueUpgradeDuration(Instant upgradingAt, DeploymentInstanceSpec instanceSpec) {
        Optional<Instant> lastOpened = Optional.empty(); // When the upgrade window most recently opened
        Instant oneWeekAgo = upgradingAt.minus(Duration.ofDays(7));
        Duration step = Duration.ofHours(1);
        for (Instant instant = upgradingAt.truncatedTo(ChronoUnit.HOURS); !instanceSpec.canUpgradeAt(instant); instant = instant.minus(step)) {
            if (!instant.isAfter(oneWeekAgo)) { // Wrapped around, the entire week is being blocked
                lastOpened = Optional.empty();
                break;
            }
            lastOpened = Optional.of(instant);
        }
        if (lastOpened.isEmpty()) return Duration.ZERO;
        return Duration.between(lastOpened.get(), upgradingAt);
    }

    private void reportQueuedNameServiceRequests() {
        metric.set(NAME_SERVICE_REQUESTS_QUEUED, controller().curator().readNameServiceQueue().requests().size(),
                   metric.createContext(Map.of()));
    }

    private void reportNodeCount(Set<NodeVersion> nodeVersions, String metricName) {
        Map<NodeCountKey, Long> newNodeCounts = nodeVersions.stream()
                                                            .collect(Collectors.groupingBy(nodeVersion -> {
                                                                return new NodeCountKey(metricName,
                                                                                        nodeVersion.currentVersion(),
                                                                                        nodeVersion.zone());
                                                            }, Collectors.counting()));
        nodeCounts.putAll(newNodeCounts);
        nodeCounts.forEach((key, count) -> {
            if (newNodeCounts.containsKey(key)) {
                // Version is still present: Update the metric.
                metric.set(metricName, count, metric.createContext(dimensions(key.zone, key.version)));
            } else if (key.metricName.equals(metricName)) {
                // Version is no longer present, but has been previously reported: Set it to zero.
                metric.set(metricName, 0, metric.createContext(dimensions(key.zone, key.version)));
            }
        });
    }

    private void reportChangeDurations(Map<NodeVersion, Duration> changeDurations, String metricName) {
        changeDurations.forEach((nodeVersion, duration) -> {
            metric.set(metricName, duration.toSeconds(), metric.createContext(dimensions(nodeVersion.hostname(), nodeVersion.zone())));
        });
    }

    private void reportTenantMetrics() {
        if (! controller().system().isPublic()) return;

        var planCounter = new TreeMap<String, Integer>();

        controller().tenants().asList().forEach(tenant -> {
            var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
            planCounter.merge(planId.value(), 1, Integer::sum);
        });

        planCounter.forEach((planId, count) -> {
            var context = metric.createContext(Map.of("plan", planId));
            metric.set(TENANT_METRIC, count, context);
        });
    }

    private void reportZmsQuotaMetrics() {
        var quota = zmsClient.getQuotaUsage();
        reportZmsQuota("subdomains", quota.getSubdomainUsage());
        reportZmsQuota("services", quota.getServiceUsage());
        reportZmsQuota("policies", quota.getPolicyUsage());
        reportZmsQuota("roles", quota.getRoleUsage());
        reportZmsQuota("groups", quota.getGroupUsage());
    }

    private void reportZmsQuota(String resourceType, double usage) {
        var context = metric.createContext(Map.of("resourceType", resourceType));
        metric.set(ZMS_QUOTA_USAGE, usage, context);
    }

    private Map<NodeVersion, Duration> platformChangeDurations(VersionStatus versionStatus) {
        return changeDurations(versionStatus.versions(), VespaVersion::nodeVersions);
    }

    private Map<NodeVersion, Duration> osChangeDurations() {
        return changeDurations(controller().osVersionStatus().versions().values(), Function.identity());
    }

    private <V> Map<NodeVersion, Duration> changeDurations(Collection<V> versions, Function<V, List<NodeVersion>> versionsGetter) {
        var now = clock.instant();
        var durations = new HashMap<NodeVersion, Duration>();
        for (var version : versions) {
            for (var nodeVersion : versionsGetter.apply(version)) {
                durations.put(nodeVersion, nodeVersion.changeDuration(now));
            }
        }
        return durations;
    }

    private static double deploymentFailRatio(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .mapToInt(status -> status.hasFailures() ? 1 : 0)
                       .average().orElse(0);
    }

    private static Map<ApplicationId, Duration> averageDeploymentDurations(DeploymentStatusList statuses, Instant now) {
        return statuses.asList().stream()
                       .flatMap(status -> status.instanceJobs().entrySet().stream())
                       .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                             entry -> averageDeploymentDuration(entry.getValue(), now)));
    }

    private static Map<ApplicationId, Integer> deploymentsFailingUpgrade(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .flatMap(status -> status.instanceJobs().entrySet().stream())
                       .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                             entry -> deploymentsFailingUpgrade(entry.getValue())));
    }

    private static int deploymentsFailingUpgrade(JobList jobs) {
        return jobs.failingHard().not().failingApplicationChange().size();
    }

    private static Duration averageDeploymentDuration(JobList jobs, Instant now) {
        List<Duration> jobDurations = jobs.lastTriggered()
                                          .mapToList(run -> Duration.between(run.start(), run.end().orElse(now)));
        return jobDurations.stream()
                           .reduce(Duration::plus)
                           .map(totalDuration -> totalDuration.dividedBy(jobDurations.size()))
                           .orElse(Duration.ZERO);
    }

    private static Map<ApplicationId, Integer> deploymentWarnings(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .flatMap(status -> status.application().instances().values().stream())
                       .collect(Collectors.toMap(Instance::id, a -> maxWarningCountOf(a.deployments().values())));
    }

    private static int maxWarningCountOf(Collection<Deployment> deployments) {
        return deployments.stream()
                          .map(Deployment::metrics)
                          .map(DeploymentMetrics::warnings)
                          .map(Map::values)
                          .flatMap(Collection::stream)
                          .max(Integer::compareTo)
                          .orElse(0);
    }

    private static Map<String, String> dimensions(ApplicationId application) {
        return Map.of("tenant", application.tenant().value(),
                      "app", application.application().value() + "." + application.instance().value(),
                      "applicationId", application.toFullString());
    }

    private static Map<String, String> dimensions(HostName hostname, ZoneId zone) {
        return Map.of("host", hostname.value(),
                      "zone", zone.value());
    }

    private static Map<String, String> dimensions(ZoneId zone, Version currentVersion) {
        return Map.of("zone", zone.value(),
                      "currentVersion", currentVersion.toFullString());
    }

    private static class NodeCountKey {

        private final String metricName;
        private final Version version;
        private final ZoneId zone;

        public NodeCountKey(String metricName, Version version, ZoneId zone) {
            this.metricName = metricName;
            this.version = version;
            this.zone = zone;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeCountKey that = (NodeCountKey) o;
            return metricName.equals(that.metricName) &&
                   version.equals(that.version) &&
                   zone.equals(that.zone);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metricName, version, zone);
        }
    }

}
