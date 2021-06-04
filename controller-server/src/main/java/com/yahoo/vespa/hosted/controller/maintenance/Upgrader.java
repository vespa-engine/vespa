// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.InstanceList;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;
import static java.util.Comparator.naturalOrder;

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
    public boolean maintain() {
        // Determine target versions for each upgrade policy
        VersionStatus versionStatus = controller().readVersionStatus();
        Version canaryTarget = controller().systemVersion(versionStatus);
        Collection<Version> defaultTargets = targetVersions(Confidence.normal, versionStatus);
        Collection<Version> conservativeTargets = targetVersions(Confidence.high, versionStatus);

        // Cancel upgrades to broken targets (let other ongoing upgrades complete to avoid starvation)
        for (VespaVersion version : versionStatus.versions()) {
            if (version.confidence() == Confidence.broken)
                cancelUpgradesOf(instances().upgradingTo(version.versionNumber())
                                            .not().with(UpgradePolicy.canary),
                                 version.versionNumber() + " is broken");
        }

        // Canaries should always try the canary target
        cancelUpgradesOf(instances().upgrading()
                                    .not().upgradingTo(canaryTarget)
                                    .with(UpgradePolicy.canary),
                         "Outdated target version for Canaries");

        // Cancel *failed* upgrades to earlier versions, as the new version may fix it
        String reason = "Failing on outdated version";
        cancelUpgradesOf(instances().upgrading()
                                    .failing()
                                    .not().upgradingTo(defaultTargets)
                                    .with(UpgradePolicy.defaultPolicy),
                         reason);
        cancelUpgradesOf(instances().upgrading()
                                    .failing()
                                    .not().upgradingTo(conservativeTargets)
                                    .with(UpgradePolicy.conservative),
                         reason);

        // Schedule the right upgrades
        InstanceList instances = instances();
        Optional<Integer> targetMajorVersion = targetMajorVersion();
        upgrade(instances.with(UpgradePolicy.canary), canaryTarget, targetMajorVersion, instances.size());
        defaultTargets.forEach(target -> upgrade(instances.with(UpgradePolicy.defaultPolicy), target, targetMajorVersion, numberOfApplicationsToUpgrade()));
        conservativeTargets.forEach(target -> upgrade(instances.with(UpgradePolicy.conservative), target, targetMajorVersion, numberOfApplicationsToUpgrade()));
        return true;
    }

    /** Returns the target versions for given confidence, one per major version in the system */
    private Collection<Version> targetVersions(Confidence confidence, VersionStatus versionStatus) {
        return versionStatus.versions().stream()
                            // Ensure we never pick a version newer than the system
                            .filter(v -> !v.versionNumber().isAfter(controller().systemVersion(versionStatus)))
                            .filter(v -> v.confidence().equalOrHigherThan(confidence))
                            .map(VespaVersion::versionNumber)
                            .collect(Collectors.toMap(Version::getMajor, // Key on major version
                                                      Function.identity(),  // Use version as value
                                                      BinaryOperator.<Version>maxBy(naturalOrder()))) // Pick highest version when merging versions within this major
                            .values();
    }

    /** Returns a list of all production application instances, except those which are pinned, which we should not manipulate here. */
    private InstanceList instances() {
        return InstanceList.from(controller().jobController().deploymentStatuses(ApplicationList.from(controller().applications().readable())))
                           .withDeclaredJobs()
                           .unpinned();
    }

    private void upgrade(InstanceList instances, Version version, Optional<Integer> targetMajorVersion, int numberToUpgrade) {
        instances.not().failingOn(version)
                 .allowMajorVersion(version.getMajor(), targetMajorVersion.orElse(version.getMajor()))
                 .not().deploying()
                 .onLowerVersionThan(version)
                 .canUpgradeAt(version, controller().clock().instant())
                 .shuffle(random) // Shuffle so we do not always upgrade instances in the same order
                 .byIncreasingDeployedVersion()
                 .first(numberToUpgrade).asList()
                 .forEach(instance -> controller().applications().deploymentTrigger().triggerChange(instance, Change.of(version)));
    }

    private void cancelUpgradesOf(InstanceList instances, String reason) {
        instances = instances.unpinned();
        if (instances.isEmpty()) return;
        log.info("Cancelling upgrading of " + instances.asList().size() + " instances: " + reason);
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

    /** Returns the target major version for applications not specifying one */
    public Optional<Integer> targetMajorVersion() {
        return curator.readTargetMajorVersion();
    }

    /** Sets the default target major version. Set to empty to determine target version normally (by confidence) */
    public void setTargetMajorVersion(Optional<Integer> targetMajorVersion) {
        curator.writeTargetMajorVersion(targetMajorVersion);
    }

    /** Override confidence for given version. This will cause the computed confidence to be ignored */
    public void overrideConfidence(Version version, Confidence confidence) {
        try (Lock lock = curator.lockConfidenceOverrides()) {
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
