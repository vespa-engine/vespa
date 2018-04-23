// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableList;
import com.yahoo.collections.ListMap;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitSha;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.JobList;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError.outOfCapacity;

/**
 * Information about the current platform versions in use.
 * The versions in use are the set of all versions running in current applications, versions
 * of config servers in all zones, and the version of this controller itself.
 * 
 * This is immutable.
 * 
 * @author bratseth
 */
public class VersionStatus {

    private static final Logger log = Logger.getLogger(VersionStatus.class.getName());

    private static final String VESPA_REPO = "vespa-yahoo";
    private static final String VESPA_REPO_OWNER = "vespa";

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
        ListMap<Version, HostName> configServerVersions = findConfigServerVersions(controller);
        ListMap<Version, HostName> controllerVersions = findControllerVersions(controller);

        Set<Version> infrastructureVersions = new HashSet<>();
        infrastructureVersions.addAll(controllerVersions.keySet());
        infrastructureVersions.addAll(configServerVersions.keySet());

        // The controller version is the lowest controller version of all controllers
        Version controllerVersion = controllerVersions.keySet().stream().sorted().findFirst().get();

        // The system version is the oldest infrastructure version
        Version systemVersion = infrastructureVersions.stream().sorted().findFirst().get();

        Collection<DeploymentStatistics> deploymentStatistics = computeDeploymentStatistics(infrastructureVersions,
                                                                                            controller.applications().asList());
        List<VespaVersion> versions = new ArrayList<>();

        for (DeploymentStatistics statistics : deploymentStatistics) {
            if (statistics.version().isEmpty()) continue;

            try {
                VespaVersion vespaVersion = createVersion(statistics,
                                                          statistics.version().equals(controllerVersion),
                                                          statistics.version().equals(systemVersion),
                                                          configServerVersions.getList(statistics.version()),
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

    private static ListMap<Version, HostName> findConfigServerVersions(Controller controller) {
        List<URI> configServers = controller.zoneRegistry().zones()
                .controllerManaged()
                // TODO jvenstad: Remove this when AWS is automatically upgraded.
                .not().among(ZoneId.from("prod.cd-us-east-1a"),
                             ZoneId.from("prod.cd-aws-us-east-1a"),
                             ZoneId.from("prod.aws-us-east-1a"),
                             ZoneId.from("dev.aws-us-east-2a"))
                .ids().stream()
                .flatMap(zoneId -> controller.zoneRegistry().getConfigServerUris(zoneId).stream())
                .collect(Collectors.toList());

        ListMap<Version, HostName> versions = new ListMap<>();
        for (URI configServer : configServers)
            versions.put(controller.configServer().version(configServer).current(),
                         HostName.from(configServer.getHost()));
        return versions;
    }

    private static ListMap<Version, HostName> findControllerVersions(Controller controller) {
        ListMap<Version, HostName> versions = new ListMap<>();
        if (controller.curator().cluster().isEmpty()) { // Use vtag if we do not have cluster
            versions.put(Vtag.currentVersion, controller.hostname());
        } else {
            for (HostName hostname : controller.curator().cluster()) {
                versions.put(controller.curator().readControllerVersion(hostname), hostname);
            }
        }
        return versions;
    }

    private static Collection<DeploymentStatistics> computeDeploymentStatistics(Set<Version> infrastructureVersions,
                                                                                List<Application> applications) {
        Map<Version, DeploymentStatistics> versionMap = new HashMap<>();

        for (Version infrastructureVersion : infrastructureVersions) {
            versionMap.put(infrastructureVersion, DeploymentStatistics.empty(infrastructureVersion));
        }

        ApplicationList applicationList = ApplicationList.from(applications)
                                                         .notPullRequest()
                                                         .hasProductionDeployment();
        for (Application application : applicationList.asList()) {
            // Note that each version deployed on this application in production exists
            // (ignore non-production versions)
            for (Deployment deployment : application.productionDeployments().values()) {
                versionMap.computeIfAbsent(deployment.version(), DeploymentStatistics::empty);
            }

            // List versions which have failing jobs, versions which are in production, and versions for which there are running deployment jobs

            // Failing versions
            JobList.from(application)
                    .failing()
                    .not().failingApplicationChange()
                    .not().failingBecause(outOfCapacity)
                    .mapToList(job -> job.lastCompleted().get().version())
                    .forEach(version -> versionMap.put(version, versionMap.getOrDefault(version, DeploymentStatistics.empty(version)).withFailing(application.id())));

            // Succeeding versions
            JobList.from(application)
                    .lastSuccess().present()
                    .production()
                    .mapToList(job -> job.lastSuccess().get().version())
                    .forEach(version -> versionMap.put(version, versionMap.getOrDefault(version, DeploymentStatistics.empty(version)).withProduction(application.id())));

            // Deploying versions
            JobList.from(application)
                    .upgrading()
                    .mapToList(job -> job.lastTriggered().get().version())
                    .forEach(version -> versionMap.put(version, versionMap.getOrDefault(version, DeploymentStatistics.empty(version)).withDeploying(application.id())));
        }
        return versionMap.values();
    }
    
    private static VespaVersion createVersion(DeploymentStatistics statistics,
                                              boolean isControllerVersion,
                                              boolean isSystemVersion, 
                                              Collection<HostName> configServerHostnames,
                                              Controller controller) {
        GitSha gitSha = controller.gitHub().getCommit(VESPA_REPO_OWNER, VESPA_REPO, statistics.version().toFullString());
        Instant committedAt = Instant.ofEpochMilli(gitSha.commit.author.date.getTime());
        VespaVersion.Confidence confidence = controller.curator().readConfidenceOverrides().get(statistics.version());
        // Compute confidence if there's no override
        if (confidence == null) {
            if (isSystemVersion || isControllerVersion) { // Always compute confidence for system and controller
                confidence = VespaVersion.confidenceFrom(statistics, controller);
            } else { // Keep existing confidence for non-system versions if already computed
                confidence = confidenceFor(statistics.version(), controller)
                        .orElse(VespaVersion.confidenceFrom(statistics, controller));
            }
        }
        return new VespaVersion(statistics,
                                gitSha.sha, committedAt,
                                isControllerVersion,
                                isSystemVersion,
                                configServerHostnames,
                                confidence
        );
    }

    /** Returns the current confidence for the given version */
    private static Optional<VespaVersion.Confidence> confidenceFor(Version version, Controller controller) {
        return controller.versionStatus().versions().stream()
                .filter(v -> version.equals(v.versionNumber()))
                .map(VespaVersion::confidence)
                .findFirst();
    }

}
