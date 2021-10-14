// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
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
        Version systemVersion = controller().readSystemVersion();

        for (Application application : controller().applications().readable())
            for (Instance instance : application.instances().values())
                for (Deployment deployment : instance.deployments().values())
                    try {
                        attempts.incrementAndGet();
                        JobId job = new JobId(instance.id(), JobType.from(controller().system(), deployment.zone()).get());
                        if ( ! deployment.zone().environment().isManuallyDeployed()) continue;

                        Run last = controller().jobController().last(job).get();
                        Versions target = new Versions(systemVersion, last.versions().targetApplication(), Optional.of(last.versions().targetPlatform()), Optional.of(last.versions().targetApplication()));
                        if ( ! deployment.version().isBefore(target.targetPlatform())) continue;
                        if ( ! isLikelyNightFor(job)) continue;

                        log.log(Level.FINE, "Upgrading deployment of " + instance.id() + " in " + deployment.zone());
                        controller().jobController().start(instance.id(), JobType.from(controller().system(), deployment.zone()).get(), target, true);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        log.log(Level.WARNING, "Failed upgrading " + deployment + " of " + instance +
                                               ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                               interval());
                    }
        return asSuccessFactor(attempts.get(), failures.get());
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
