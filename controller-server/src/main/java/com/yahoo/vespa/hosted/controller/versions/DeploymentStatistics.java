// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.function.Function.identity;

/**
 * Statistics about deployments on a platform version. This is immutable.
 *
 * @author jonmv
 */
public class DeploymentStatistics {
    
    private final Version version;
    private final List<Run> failingUpgrades;
    private final List<Run> otherFailing;
    private final List<Run> productionSuccesses;
    private final List<Run> runningUpgrade;
    private final List<Run> otherRunning;

    public DeploymentStatistics(Version version, List<Run> failingUpgrades, List<Run> otherFailing,
                                List<Run> productionSuccesses, List<Run> runningUpgrade, List<Run> otherRunning) {
        this.version = Objects.requireNonNull(version);
        this.failingUpgrades = List.copyOf(failingUpgrades);
        this.otherFailing = List.copyOf(otherFailing);
        this.productionSuccesses = List.copyOf(productionSuccesses);
        this.runningUpgrade = List.copyOf(runningUpgrade);
        this.otherRunning = List.copyOf(otherRunning);
    }

    /** Returns the version these statistics are for. */
    public Version version() { return version; }

    /** Returns the runs on the version of this, for currently failing instances, where the failure may be because of the upgrade. */
    public List<Run> failingUpgrades() { return failingUpgrades; }

    /** Returns all other failing runs on the version of this, for currently failing instances. */
    public List<Run> otherFailing() { return otherFailing; }

    /** Returns the production runs where the last success was on the version of this. */
    public List<Run> productionSuccesses() { return productionSuccesses; }

    /** Returns the currently running runs on the version of this, where an upgrade is attempted. */
    public List<Run> runningUpgrade() { return runningUpgrade; }

    /** Returns all other currently running runs on the version on this. */
    public List<Run> otherRunning() { return otherRunning; }

    public static List<DeploymentStatistics> compute(Collection<Version> infrastructureVersions, DeploymentStatusList statuses) {

        Set<Version> allVersions = new HashSet<>(infrastructureVersions);
        Map<Version, List<Run>> failingUpgrade = new HashMap<>();
        Map<Version, List<Run>> otherFailing = new HashMap<>();
        Map<Version, List<Run>> productionSuccesses = new HashMap<>();
        Map<Version, List<Run>> runningUpgrade = new HashMap<>();
        Map<Version, List<Run>> otherRunning = new HashMap<>();

        for (DeploymentStatus status : statuses.asList()) {
            if (status.application().projectId().isEmpty())
                continue;

            for (Instance instance : status.application().instances().values())
                for (Deployment deployment : instance.productionDeployments().values())
                    allVersions.add(deployment.version());

            JobList failing = status.jobs().failing()
                                    .not().withStatus(RunStatus.outOfCapacity)
                                    .not().withStatus(RunStatus.aborted);

            // Add all unsuccessful runs for failing production jobs as any run may have resulted in an incomplete deployment
            // where a subset of nodes have upgraded.
            failing.not().failingApplicationChange()
                   .production()
                   .mapToList(JobStatus::runs)
                   .forEach(runs -> runs.descendingMap().values().stream()
                                        .dropWhile(run -> ! run.hasEnded())
                                        .takeWhile(run -> run.hasFailed())
                                        .forEach(run -> {
                                            failingUpgrade.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                                            if (failingUpgrade.get(run.versions().targetPlatform()).stream().noneMatch(existing -> existing.id().job().equals(run.id().job())))
                                                failingUpgrade.get(run.versions().targetPlatform()).add(run);
                                        }));

            // Add only the last failing run for test jobs.
            failing.not().failingApplicationChange()
                   .not().production()
                   .lastCompleted().asList()
                   .forEach(run -> {
                       failingUpgrade.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                       failingUpgrade.get(run.versions().targetPlatform()).add(run);
                   });

            // Add only the last failing for instances failing only an application change, i.e., no upgrade.
            failing.failingApplicationChange()
                   .lastCompleted().asList()
                   .forEach(run -> {
                       otherFailing.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                       otherFailing.get(run.versions().targetPlatform()).add(run);
                   });

            status.jobs().production()
                  .lastSuccess().asList()
                  .forEach(run -> {
                      productionSuccesses.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                      productionSuccesses.get(run.versions().targetPlatform()).add(run);
                  });

            JobList running = status.jobs().running();
            running.upgrading()
                   .lastTriggered().asList()
                   .forEach(run -> {
                       runningUpgrade.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                       runningUpgrade.get(run.versions().targetPlatform()).add(run);
                   });

            running.not().upgrading()
                   .lastTriggered().asList()
                   .forEach(run -> {
                       otherRunning.putIfAbsent(run.versions().targetPlatform(), new ArrayList<>());
                       otherRunning.get(run.versions().targetPlatform()).add(run);
                   });
        }

        return Stream.of(allVersions.stream(),
                         failingUpgrade.keySet().stream(),
                         otherFailing.keySet().stream(),
                         productionSuccesses.keySet().stream(),
                         runningUpgrade.keySet().stream(),
                         otherRunning.keySet().stream())
                     .flatMap(identity()) // Lol.
                     .distinct()
                     .sorted(naturalOrder())
                     .map(version -> new DeploymentStatistics(version,
                                                              failingUpgrade.getOrDefault(version, List.of()),
                                                              otherFailing.getOrDefault(version, List.of()),
                                                              productionSuccesses.getOrDefault(version, List.of()),
                                                              runningUpgrade.getOrDefault(version, List.of()),
                                                              otherRunning.getOrDefault(version, List.of())))
                     .collect(Collectors.toUnmodifiableList());

    }

}
