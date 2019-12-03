// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.maintenance.SystemUpgrader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Information about the current platform versions in use.
 * The versions in use are the set of all versions running in current applications, versions
 * of config servers in all zones, and the version of this controller itself.
 * 
 * This is immutable.
 * 
 * @author bratseth
 * @author mpolden
 */
public class VersionStatus {

    private static final Logger log = Logger.getLogger(VersionStatus.class.getName());

    private final ImmutableList<VespaVersion> versions;
    
    /** Create a version status. DO NOT USE: Public for testing and serialization only */
    public VersionStatus(List<VespaVersion> versions) {
        this.versions = ImmutableList.copyOf(versions);
    }

    /** Returns the current version of controllers in this system */
    public Optional<VespaVersion> controllerVersion() {
        return versions().stream().filter(VespaVersion::isControllerVersion).findFirst();
    }
    
    /** 
     * Returns the current Vespa version of the system controlled by this, 
     * or empty if we have not currently determined what the system version is in this status.
     */
    public Optional<VespaVersion> systemVersion() {
        return versions().stream().filter(VespaVersion::isSystemVersion).findFirst();
    }

    /** Returns whether the system is currently upgrading */
    public boolean isUpgrading() {
        return systemVersion().map(VespaVersion::versionNumber).orElse(Version.emptyVersion)
                              .isBefore(controllerVersion().map(VespaVersion::versionNumber)
                                                           .orElse(Version.emptyVersion));
    }

    /** 
     * Lists all currently active Vespa versions, with deployment statistics, 
     * sorted from lowest to highest version number.
     * The returned list is immutable.
     * Calling this is free, but the returned status is slightly out of date.
     */
    public List<VespaVersion> versions() { return versions; }
    
    /** Returns the given version, or null if it is not present */
    public VespaVersion version(Version version) {
        return versions.stream().filter(v -> v.versionNumber().equals(version)).findFirst().orElse(null);
    }

    /** Create the empty version status */
    public static VersionStatus empty() { return new VersionStatus(ImmutableList.of()); }

    /** Create a full, updated version status. This is expensive and should be done infrequently */
    public static VersionStatus compute(Controller controller) {
        var systemApplicationVersions = findSystemApplicationVersions(controller);
        var controllerVersions = findControllerVersions(controller);

        var infrastructureVersions = ArrayListMultimap.<Version, HostName>create();
        for (var kv : controllerVersions.asMap().entrySet()) {
            infrastructureVersions.putAll(kv.getKey().version(), kv.getValue());
        }
        infrastructureVersions.putAll(systemApplicationVersions.asVersionMap());

        // The system version is the oldest infrastructure version, if that version is newer than the current system
        // version
        Version newSystemVersion = infrastructureVersions.keySet().stream().min(Comparator.naturalOrder()).get();
        Version systemVersion = controller.versionStatus().systemVersion()
                                          .map(VespaVersion::versionNumber)
                                          .orElse(newSystemVersion);
        if (newSystemVersion.isBefore(systemVersion)) {
            log.warning("Refusing to lower system version from " +
                        controller.systemVersion() +
                        " to " +
                        newSystemVersion +
                        ", nodes on " + newSystemVersion + ": " +
                        infrastructureVersions.get(newSystemVersion).stream()
                                              .map(HostName::value)
                                              .collect(Collectors.joining(", ")));
        } else {
            systemVersion = newSystemVersion;
        }


        var deploymentStatistics = computeDeploymentStatistics(infrastructureVersions.keySet(),
                                                               controller.jobController().deploymentStatuses(ApplicationList.from(controller.applications().asList())));
        List<VespaVersion> versions = new ArrayList<>();
        List<Version> releasedVersions = controller.mavenRepository().metadata().versions();

        for (DeploymentStatistics statistics : deploymentStatistics) {
            if (statistics.version().isEmpty()) continue;

            try {
                boolean isReleased = Collections.binarySearch(releasedVersions, statistics.version()) >= 0;
                VespaVersion vespaVersion = createVersion(statistics,
                                                          controllerVersions.keySet(),
                                                          systemVersion,
                                                          isReleased,
                                                          systemApplicationVersions.matching(statistics.version()),
                                                          controller);
                versions.add(vespaVersion);
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Unable to create VespaVersion for version " +
                                       statistics.version().toFullString(), e);
            }
        }

        Collections.sort(versions);

        return new VersionStatus(versions);
    }

    private static NodeVersions findSystemApplicationVersions(Controller controller) {
        var nodeVersions = controller.versionStatus().systemVersion()
                                     .map(VespaVersion::nodeVersions)
                                     .orElse(NodeVersions.EMPTY);
        var newNodeVersions = new ArrayList<NodeVersion>();
        for (var zone : controller.zoneRegistry().zones().controllerUpgraded().zones()) {
            for (var application : SystemApplication.all()) {
                var nodes = controller.serviceRegistry().configServer().nodeRepository()
                                      .list(zone.getId(), application.id()).stream()
                                      .filter(SystemUpgrader::eligibleForUpgrade)
                                      .collect(Collectors.toList());
                if (nodes.isEmpty()) continue;
                var configConverged = application.configConvergedIn(zone.getId(), controller, Optional.empty());
                if (!configConverged) {
                    log.log(LogLevel.WARNING, "Config for " + application.id() + " in " + zone.getId() +
                                              " has not converged");
                }
                var now = controller.clock().instant();
                for (var node : nodes) {
                    // Only use current node version if config has converged
                    Version version = configConverged ? node.currentVersion() : controller.systemVersion();
                    newNodeVersions.add(new NodeVersion(node.hostname(), zone.getId(), version, node.wantedVersion(), now));
                }
            }
        }
        return nodeVersions.with(newNodeVersions);
    }

    private static ListMultimap<ControllerVersion, HostName> findControllerVersions(Controller controller) {
        ListMultimap<ControllerVersion, HostName> versions = ArrayListMultimap.create();
        if (controller.curator().cluster().isEmpty()) { // Use vtag if we do not have cluster
            versions.put(ControllerVersion.CURRENT, controller.hostname());
        } else {
            for (HostName hostname : controller.curator().cluster()) {
                versions.put(controller.curator().readControllerVersion(hostname), hostname);
            }
        }
        return versions;
    }

    private static Collection<DeploymentStatistics> computeDeploymentStatistics(Set<Version> infrastructureVersions,
                                                                                DeploymentStatusList statuses) {
        Map<Version, DeploymentStatistics> versionMap = new HashMap<>();

        for (Version infrastructureVersion : infrastructureVersions) {
            versionMap.put(infrastructureVersion, DeploymentStatistics.empty(infrastructureVersion));
        }

        for (DeploymentStatus status : statuses.withProductionDeployment().asList()) {
            for (Instance instance : status.application().instances().values())
                for (Deployment deployment : instance.productionDeployments().values())
                    versionMap.computeIfAbsent(deployment.version(), DeploymentStatistics::empty);

            status.instanceJobs().forEach((id, jobs) -> {
                jobs.failing()
                    .not().failingApplicationChange()
                    .not().withStatus(RunStatus.outOfCapacity)
                    .lastCompleted().mapToList(run -> run.versions().targetPlatform())
                    .forEach(version -> versionMap.put(version,
                                                       versionMap.getOrDefault(version, DeploymentStatistics.empty(version))
                                                                 .withFailing(id)));

                jobs.production()
                    .lastSuccess().mapToList(run -> run.versions().targetPlatform())
                    .forEach(version -> versionMap.put(version,
                                                       versionMap.getOrDefault(version, DeploymentStatistics.empty(version))
                                                                 .withProduction(id)));

                jobs.upgrading()
                    .lastTriggered().mapToList(run -> run.versions().targetPlatform())
                    .forEach(version -> versionMap.put(version,
                                                       versionMap.getOrDefault(version, DeploymentStatistics.empty(version))
                                                                 .withDeploying(id)));
            });
        }
        return versionMap.values();
    }

    private static VespaVersion createVersion(DeploymentStatistics statistics,
                                              Set<ControllerVersion> controllerVersions,
                                              Version systemVersion,
                                              boolean isReleased,
                                              NodeVersions nodeVersions,
                                              Controller controller) {
        var latestVersion = controllerVersions.stream().max(Comparator.naturalOrder()).get();
        var controllerVersion = controllerVersions.stream().min(Comparator.naturalOrder()).get();
        var isSystemVersion = statistics.version().equals(systemVersion);
        var isControllerVersion = statistics.version().equals(controllerVersion.version());
        var confidence = controller.curator().readConfidenceOverrides().get(statistics.version());
        var confidenceIsOverridden = confidence != null;
        var previousStatus = controller.versionStatus().version(statistics.version());

        // Compute confidence
        if (!confidenceIsOverridden) {
            // Always compute confidence for system and controller
            if (isSystemVersion || isControllerVersion) {
                confidence = VespaVersion.confidenceFrom(statistics, controller);
            } else {
                // This is an older version so we preserve the existing confidence, if any
                confidence = getOrUpdateConfidence(statistics, controller);
            }
        }

        // Preserve existing commit details if we've previously computed status for this version
        var commitSha = latestVersion.commitSha();
        var commitDate = latestVersion.commitDate();
        if (previousStatus != null) {
            commitSha = previousStatus.releaseCommit();
            commitDate = previousStatus.committedAt();

            // Keep existing confidence if we cannot raise it at this moment in time
            if (!confidenceIsOverridden &&
                !previousStatus.confidence().canChangeTo(confidence, controller.clock().instant())) {
                confidence = previousStatus.confidence();
            }
        }

        return new VespaVersion(statistics,
                                commitSha,
                                commitDate,
                                isControllerVersion,
                                isSystemVersion,
                                isReleased,
                                nodeVersions,
                                confidence);
    }

    /**
     * Calculate confidence from given deployment statistics.
     *
     * @return previously calculated confidence for this version. If none exists, a new confidence will be calculated.
     */
    private static VespaVersion.Confidence getOrUpdateConfidence(DeploymentStatistics statistics, Controller controller) {
        return controller.versionStatus().versions().stream()
                         .filter(v -> statistics.version().equals(v.versionNumber()))
                         .map(VespaVersion::confidence)
                         .findFirst()
                         .orElseGet(() -> VespaVersion.confidenceFrom(statistics, controller));
    }

}
