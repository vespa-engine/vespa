// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Upgrades instances in manually deployed zones to the system version, at a convenient time.
 * 
 * @author jonmv
 */
public class DeploymentUpgrader extends ControllerMaintainer {

    public DeploymentUpgrader(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Version targetPlatform = null; // Upgrade to the newest non-broken, deployable version.
        VersionStatus versionStatus = controller().readVersionStatus();
        for (VespaVersion platform : versionStatus.deployableVersions())
            if (platform.confidence().equalOrHigherThan(VespaVersion.Confidence.normal))
                targetPlatform = platform.versionNumber();

        if (targetPlatform == null)
            return 0;

        for (Application application : controller().applications().readable())
            for (Instance instance : application.instances().values())
                for (Deployment deployment : instance.deployments().values())
                    try {
                        JobId job = new JobId(instance.id(), JobType.deploymentTo(deployment.zone()));
                        if ( ! deployment.zone().environment().isManuallyDeployed()) continue;

                        Run last = controller().jobController().last(job).get();
                        Versions target = new Versions(targetPlatform, last.versions().targetRevision(), Optional.of(last.versions().targetPlatform()), Optional.of(last.versions().targetRevision()));
                        if ( ! last.hasEnded()) continue;
                        ApplicationVersion devVersion = application.revisions().get(last.versions().targetRevision());
                        if (devVersion.compileVersion()
                                      .map(version -> controller().applications().versionCompatibility(instance.id()).refuse(version, target.targetPlatform()))
                                      .orElse(false)) continue;
                        if (   devVersion.allowedMajor().isPresent()
                            && devVersion.allowedMajor().get() < targetPlatform.getMajor()) continue;

                        if ( ! deployment.version().isBefore(target.targetPlatform())) continue;
                        if ( ! isLikelyNightFor(job)) continue;
                        if (deployment.zone().environment() == Environment.perf && ! isIdleOrOutdated(deployment, job)) continue;

                        log.log(Level.FINE, "Upgrading deployment of " + instance.id() + " in " + deployment.zone());
                        attempts.incrementAndGet();
                        controller().jobController().start(instance.id(), JobType.deploymentTo(deployment.zone()), target, true, Optional.of("automated upgrade"));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        log.log(Level.WARNING, "Failed upgrading " + deployment + " of " + instance +
                                               ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                               interval());
                    }
        return asSuccessFactor(attempts.get(), failures.get());
    }

    /** Returns whether query and feed metrics are ~zero, or currently platform has been deployed for a week. */
    private boolean isIdleOrOutdated(Deployment deployment, JobId job) {
        if (deployment.metrics().queriesPerSecond() <= 1 && deployment.metrics().writesPerSecond() <= 1) return true;
        return controller().jobController().runs(job).descendingMap().values().stream()
                           .takeWhile(run -> run.versions().targetPlatform().equals(deployment.version()))
                           .anyMatch(run -> run.start().isBefore(controller().clock().instant().minus(Duration.ofDays(7))));
    }

    private boolean isLikelyNightFor(JobId job) {
        int hour = hourOf(controller().clock().instant());
        int[] runStarts = controller().jobController().jobStarts(job).stream()
                                      .mapToInt(DeploymentUpgrader::hourOf)
                                      .toArray();
        int localNight = mostLikelyWeeHour(runStarts);
        return Math.abs(hour - localNight) <= 1;
    }

    static int mostLikelyWeeHour(int[] starts) {
        double weight = 1;
        double[] buckets = new double[24];
        for (int start : starts)
            buckets[start] += weight *= 0.8; // Weight more recent deployments higher.

        int best = -1;
        double min = Double.MAX_VALUE;
        for (int i = 12; i < 36; i++) {
            double sum = 0;
            for (int j = -12; j < 12; j++)
                sum += buckets[(i + j) % 24] / (Math.abs(j) + 1);

            if (sum < min) {
                min = sum;
                best = i;
            }
        }
        return (best + 2) % 24;
    }

    private static int hourOf(Instant instant) {
        return (int) (instant.toEpochMilli() / 3_600_000 % 24);
    }

}
