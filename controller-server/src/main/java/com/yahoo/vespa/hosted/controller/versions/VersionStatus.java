// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.maintenance.SystemUpgrader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private final List<VespaVersion> versions;
    
    /** Create a version status. DO NOT USE: Public for testing and serialization only */
    public VersionStatus(List<VespaVersion> versions) {
        this.versions = List.copyOf(versions);
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

    /** Returns whether given version is active in this system */
    public boolean isActive(Version version) {
        return version(version) != null;
    }

    /** Create the empty version status */
    public static VersionStatus empty() { return new VersionStatus(List.of()); }

    /** Create a full, updated version status. This is expensive and should be done infrequently */
    public static VersionStatus compute(Controller controller) {
        VersionStatus versionStatus = controller.readVersionStatus();
        NodeVersions systemApplicationVersions = findSystemApplicationVersions(controller, versionStatus);
        Map<ControllerVersion, List<HostName>> controllerVersions = findControllerVersions(controller);

        Map<Version, List<HostName>> infrastructureVersions = new HashMap<>();
        for (var kv : controllerVersions.entrySet()) {
            infrastructureVersions.computeIfAbsent(kv.getKey().version(), (k) -> new ArrayList<>())
                                  .addAll(kv.getValue());
        }
        for (var kv : systemApplicationVersions.asMap().entrySet()) {
            infrastructureVersions.computeIfAbsent(kv.getValue().currentVersion(), (k) -> new ArrayList<>())
                                  .add(kv.getKey());
        }

        // The system version is the oldest infrastructure version, if that version is newer than the current system
        // version
        Version newSystemVersion = infrastructureVersions.keySet().stream().min(Comparator.naturalOrder()).get();
        Version systemVersion = versionStatus.systemVersion()
                                             .map(VespaVersion::versionNumber)
                                             .orElse(newSystemVersion);
        if (newSystemVersion.isBefore(systemVersion)) {
            log.warning("Refusing to lower system version from " +
                        systemVersion +
                        " to " +
                        newSystemVersion +
                        ", nodes on " + newSystemVersion + ": " +
                        infrastructureVersions.get(newSystemVersion).stream()
                                              .map(HostName::value)
                                              .collect(Collectors.joining(", ")));
        } else {
            systemVersion = newSystemVersion;
        }


        List<DeploymentStatistics> deploymentStatistics = DeploymentStatistics.compute(infrastructureVersions.keySet(),
                                                                                       controller.jobController().deploymentStatuses(ApplicationList.from(controller.applications().asList())
                                                                                                                                                    .withProjectId()));
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
                                                          controller,
                                                          versionStatus);
                versions.add(vespaVersion);
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Unable to create VespaVersion for version " +
                                       statistics.version().toFullString(), e);
            }
        }

        Collections.sort(versions);

        return new VersionStatus(versions);
    }

    private static NodeVersions findSystemApplicationVersions(Controller controller, VersionStatus versionStatus) {
        var nodeVersions = new LinkedHashMap<HostName, NodeVersion>();
        for (var zone : controller.zoneRegistry().zones().controllerUpgraded().zones()) {
            for (var application : SystemApplication.all()) {
                var nodes = controller.serviceRegistry().configServer().nodeRepository()
                                      .list(zone.getId(), application.id()).stream()
                                      .filter(SystemUpgrader::eligibleForUpgrade)
                                      .collect(Collectors.toList());
                if (nodes.isEmpty()) continue;
                boolean configConverged = application.configConvergedIn(zone.getId(), controller, Optional.empty());
                if (!configConverged) {
                    log.log(Level.WARNING, "Config for " + application.id() + " in " + zone.getId() +
                                              " has not converged");
                }
                for (var node : nodes) {
                    // Only use current node version if config has converged
                    var version = configConverged ? node.currentVersion() : controller.systemVersion(versionStatus);
                    var nodeVersion = new NodeVersion(node.hostname(), zone.getId(), version, node.wantedVersion(),
                                                      node.suspendedSince());
                    nodeVersions.put(nodeVersion.hostname(), nodeVersion);
                }
            }
        }
        return NodeVersions.copyOf(nodeVersions);
    }

    private static Map<ControllerVersion, List<HostName>> findControllerVersions(Controller controller) {
        Map<ControllerVersion, List<HostName>> versions = new HashMap<>();
        if (controller.curator().cluster().isEmpty()) { // Use vtag if we do not have cluster
            versions.computeIfAbsent(ControllerVersion.CURRENT, (k) -> new ArrayList<>())
                    .add(controller.hostname());
        } else {
            for (String host : controller.curator().cluster()) {
                HostName hostname = HostName.from(host);
                versions.computeIfAbsent(controller.curator().readControllerVersion(hostname), (k) -> new ArrayList<>())
                        .add(hostname);
            }
        }
        return versions;
    }

    private static VespaVersion createVersion(DeploymentStatistics statistics,
                                              Set<ControllerVersion> controllerVersions,
                                              Version systemVersion,
                                              boolean isReleased,
                                              NodeVersions nodeVersions,
                                              Controller controller,
                                              VersionStatus versionStatus) {
        ControllerVersion latestVersion = controllerVersions.stream().max(Comparator.naturalOrder()).get();
        ControllerVersion controllerVersion = controllerVersions.stream().min(Comparator.naturalOrder()).get();
        boolean isSystemVersion = statistics.version().equals(systemVersion);
        boolean isControllerVersion = statistics.version().equals(controllerVersion.version());
        VespaVersion.Confidence confidence = controller.curator().readConfidenceOverrides().get(statistics.version());
        boolean confidenceIsOverridden = confidence != null;
        VespaVersion existingVespaVersion = versionStatus.version(statistics.version());

        // Compute confidence
        if (!confidenceIsOverridden) {
            // Always compute confidence for system and controller
            if (isSystemVersion || isControllerVersion) {
                confidence = VespaVersion.confidenceFrom(statistics, controller);
            } else {
                // This is an older version so we preserve the existing confidence, if any
                confidence = versionStatus.versions().stream()
                                          .filter(v -> statistics.version().equals(v.versionNumber()))
                                          .map(VespaVersion::confidence)
                                          .findFirst()
                                          .orElseGet(() -> VespaVersion.confidenceFrom(statistics, controller));
            }
        }

        // Preserve existing commit details if we've previously computed status for this version
        var commitSha = latestVersion.commitSha();
        var commitDate = latestVersion.commitDate();
        if (existingVespaVersion != null) {
            commitSha = existingVespaVersion.releaseCommit();
            commitDate = existingVespaVersion.committedAt();

            // Keep existing confidence if we cannot raise it at this moment in time
            if (!confidenceIsOverridden &&
                !existingVespaVersion.confidence().canChangeTo(confidence, controller.clock().instant())) {
                confidence = existingVespaVersion.confidence();
            }
        }

        return new VespaVersion(statistics.version(),
                                commitSha,
                                commitDate,
                                isControllerVersion,
                                isSystemVersion,
                                isReleased,
                                nodeVersions,
                                confidence);
    }

}
