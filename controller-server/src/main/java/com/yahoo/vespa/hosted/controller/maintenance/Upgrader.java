// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.InstanceList;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;

/**
 * Maintenance job which schedules applications for Vespa version upgrade
 *
 * @author bratseth
 * @author mpolden
 */
public class Upgrader extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(Upgrader.class.getName());

    private final CuratorDb curator;
    private final Random random;

    public Upgrader(Controller controller, Duration interval) {
        super(controller, interval);
        this.curator = controller.curator();
        this.random = new Random(controller.clock().instant().toEpochMilli()); // Seed with clock for test determinism
    }

    /**
     * Schedule application upgrades. Note that this implementation must be idempotent.
     */
    @Override
    public double maintain() {
        // Determine target versions for each upgrade policy
        VersionStatus versionStatus = controller().readVersionStatus();
        cancelBrokenUpgrades(versionStatus);

        DeploymentStatusList deploymentStatuses = deploymentStatuses(versionStatus);
        for (UpgradePolicy policy : UpgradePolicy.values())
            updateTargets(versionStatus, deploymentStatuses, policy);

        return 0.0;
    }

    private DeploymentStatusList deploymentStatuses(VersionStatus versionStatus) {
        return controller().jobController().deploymentStatuses(ApplicationList.from(controller().applications().readable())
                                                                              .withProjectId()
                                                                              .withJobs(),
                                                               versionStatus);
    }

    /** Returns a list of all production application instances, except those which are pinned, which we should not manipulate here. */
    private InstanceList instances(DeploymentStatusList deploymentStatuses) {
        return InstanceList.from(deploymentStatuses)
                           .withDeclaredJobs()
                           .shuffle(random)
                           .byIncreasingDeployedVersion()
                           .unpinned();
    }

    private void cancelBrokenUpgrades(VersionStatus versionStatus) {
        // Cancel upgrades to broken targets (let other ongoing upgrades complete to avoid starvation)
        InstanceList instances = instances(deploymentStatuses(controller().readVersionStatus()));
        for (VespaVersion version : versionStatus.versions()) {
            if (version.confidence() == Confidence.broken)
                cancelUpgradesOf(instances.upgradingTo(version.versionNumber()).not().with(UpgradePolicy.canary),
                                 version.versionNumber() + " is broken");
        }
    }

    private void updateTargets(VersionStatus versionStatus, DeploymentStatusList deploymentStatuses, UpgradePolicy policy) {
        InstanceList instances = instances(deploymentStatuses);
        InstanceList remaining = instances.with(policy);
        Instant failureThreshold = controller().clock().instant().minus(DeploymentTrigger.maxFailingRevisionTime);
        Set<ApplicationId> failingRevision = InstanceList.from(deploymentStatuses.failingApplicationChangeSince(failureThreshold)).asSet();

        List<Version> targetAndNewer = new ArrayList<>();
        UnaryOperator<InstanceList> cancellationCriterion = policy == UpgradePolicy.canary ? i -> i.not().upgradingTo(targetAndNewer)
                                                                                           : i -> i.failing()
                                                                                                   .not().upgradingTo(targetAndNewer);

        Map<ApplicationId, Version> targets = new LinkedHashMap<>();
        for (Version version : DeploymentStatus.targetsForPolicy(versionStatus, controller().systemVersion(versionStatus), policy)) {
            targetAndNewer.add(version);
            InstanceList eligible = eligibleForVersion(remaining, version, versionStatus);
            InstanceList outdated = cancellationCriterion.apply(eligible);
            cancelUpgradesOf(outdated.upgrading(), "Upgrading to outdated versions");

            // Prefer the newest target for each instance.
            remaining = remaining.not().matching(eligible.asList()::contains)
                                 .not().hasCompleted(Change.of(version));
            for (ApplicationId id : outdated.and(eligible.not().upgrading()))
                targets.put(id, version);
        }

        int numberToUpgrade = policy == UpgradePolicy.canary ? instances.size() : numberOfApplicationsToUpgrade();
        for (ApplicationId id : instances.matching(targets.keySet()::contains)) {
            if (failingRevision.contains(id)) {
                log.log(Level.INFO, "Cancelling failing revision for " + id);
                controller().applications().deploymentTrigger().cancelChange(id, ChangesToCancel.APPLICATION);
            }

            if (controller().applications().requireInstance(id).change().isEmpty()) {
                log.log(Level.INFO, "Triggering upgrade to " + targets.get(id) + " for " + id);
                controller().applications().deploymentTrigger().forceChange(id, Change.of(targets.get(id)));
                --numberToUpgrade;
            }
            if (numberToUpgrade <= 0) break;
        }
    }

    private InstanceList eligibleForVersion(InstanceList instances, Version version, VersionStatus versionStatus) {
        Change change = Change.of(version);
        return instances.not().failingOn(version)
                        .allowingMajorVersion(version.getMajor(), versionStatus)
                        .compatibleWithPlatform(version, controller().applications()::versionCompatibility)
                        .not().hasCompleted(change) // Avoid rescheduling change for instances without production steps.
                        .onLowerVersionThan(version)
                        .canUpgradeAt(version, controller().clock().instant());
    }

    private void cancelUpgradesOf(InstanceList instances, String reason) {
        instances = instances.unpinned();
        if (instances.isEmpty()) return;
        log.info("Cancelling upgrading of " + instances.asList() + " instances: " + reason);
        for (ApplicationId instance : instances.asList())
            controller().applications().deploymentTrigger().cancelChange(instance, PLATFORM);
    }

    /** Returns the number of applications to upgrade in this run */
    private int numberOfApplicationsToUpgrade() {
        return numberOfApplicationsToUpgrade(interval().dividedBy(Math.max(1, controller().curator().cluster().size())).toMillis(),
                                             controller().clock().millis(),
                                             upgradesPerMinute());
    }

    /** Returns the number of applications to upgrade in the interval containing now */
    static int numberOfApplicationsToUpgrade(long intervalMillis, long nowMillis, double upgradesPerMinute) {
        long intervalStart = Math.round(nowMillis / (double) intervalMillis) * intervalMillis;
        double upgradesPerMilli = upgradesPerMinute / 60_000;
        long upgradesAtStart = (long) (intervalStart * upgradesPerMilli);
        long upgradesAtEnd = (long) ((intervalStart + intervalMillis) * upgradesPerMilli);
        return (int) (upgradesAtEnd - upgradesAtStart);
    }

    /** Returns number of upgrades per minute */
    public double upgradesPerMinute() {
        return curator.readUpgradesPerMinute();
    }

    /** Sets the number of upgrades per minute */
    public void setUpgradesPerMinute(double n) {
        if (n < 0)
            throw new IllegalArgumentException("Upgrades per minute must be >= 0, got " + n);
        curator.writeUpgradesPerMinute(n);
    }

    /** Override confidence for given version. This will cause the computed confidence to be ignored */
    public void overrideConfidence(Version version, Confidence confidence) {
        if (confidence == Confidence.aborted && !version.isAfter(controller().readSystemVersion())) {
            throw new IllegalArgumentException("Cannot override confidence to " + confidence +
                                               " for version " + version.toFullString() +
                                               ": Version may be in use by applications");
        }
        try (Mutex lock = curator.lockConfidenceOverrides()) {
            Map<Version, Confidence> overrides = new LinkedHashMap<>(curator.readConfidenceOverrides());
            overrides.put(version, confidence);
            curator.writeConfidenceOverrides(overrides);
        }
    }

    /** Returns all confidence overrides */
    public Map<Version, Confidence> confidenceOverrides() {
        return curator.readConfidenceOverrides();
    }

    /** Remove confidence override for given version */
    public void removeConfidenceOverride(Version version) {
        controller().removeConfidenceOverride(version::equals);
    }

}
