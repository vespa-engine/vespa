// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintenance job which schedules applications for Vespa version upgrade
 *
 * @author bratseth
 * @author mpolden
 */
public class Upgrader extends Maintainer {

    private static final Logger log = Logger.getLogger(Upgrader.class.getName());

    private final CuratorDb curator;

    public Upgrader(Controller controller, Duration interval, JobControl jobControl, CuratorDb curator) {
        super(controller, interval, jobControl);
        this.curator = curator;
    }

    /**
     * Schedule application upgrades. Note that this implementation must be idempotent.
     */
    @Override
    public void maintain() {
        // Determine target versions for each upgrade policy
        Optional<Version> canaryTarget = controller().versionStatus().systemVersion().map(VespaVersion::versionNumber);
        Optional<Version> defaultTarget = newestVersionWithConfidence(Confidence.normal);
        Optional<Version> conservativeTarget = newestVersionWithConfidence(Confidence.high);

        // Cancel upgrades to broken targets (let other ongoing upgrades complete to avoid starvation
        for (VespaVersion version : controller().versionStatus().versions()) {
            if (version.confidence() == Confidence.broken)
                cancelUpgradesOf(applications().without(UpgradePolicy.canary).upgradingTo(version.versionNumber()),
                                 version.versionNumber() + " is broken");
        }

        // Canaries should always try the canary target
        cancelUpgradesOf(applications().with(UpgradePolicy.canary).upgrading().notUpgradingTo(canaryTarget),
                         "Outdated target version for Canaries");

        // Cancel *failed* upgrades to earlier versions, as the new version may fix it
        String reason = "Failing on outdated version";
        cancelUpgradesOf(applications().with(UpgradePolicy.defaultPolicy).upgrading().failing().notUpgradingTo(defaultTarget), reason);
        cancelUpgradesOf(applications().with(UpgradePolicy.conservative).upgrading().failing().notUpgradingTo(conservativeTarget), reason);

        // Schedule the right upgrades
        canaryTarget.ifPresent(target -> upgrade(applications().with(UpgradePolicy.canary), target));
        defaultTarget.ifPresent(target -> upgrade(applications().with(UpgradePolicy.defaultPolicy), target));
        conservativeTarget.ifPresent(target -> upgrade(applications().with(UpgradePolicy.conservative), target));
    }

    private Optional<Version> newestVersionWithConfidence(Confidence confidence) {
        return reversed(controller().versionStatus().versions()).stream()
                                                                .filter(v -> v.confidence().equalOrHigherThan(confidence))
                                                                .findFirst()
                                                                .map(VespaVersion::versionNumber);
    }

    private List<VespaVersion> reversed(List<VespaVersion> versions) {
        List<VespaVersion> reversed = new ArrayList<>(versions.size());
        for (int i = 0; i < versions.size(); i++)
            reversed.add(versions.get(versions.size() - 1 - i));
        return reversed;
    }

    /** Returns a list of all applications */
    private ApplicationList applications() { return ApplicationList.from(controller().applications().asList()); }

    private void upgrade(ApplicationList applications, Version version) {
        applications = applications.notPullRequest(); // Pull requests are deployed as separate applications to test then deleted; No need to upgrade
        applications = applications.hasProductionDeployment();
        applications = applications.onLowerVersionThan(version);
        applications = applications.notDeploying(); // wait with applications deploying an application change or already upgrading
        applications = applications.notFailingOn(version); // try to upgrade only if it hasn't failed on this version
        applications = applications.canUpgradeAt(controller().clock().instant()); // wait with applications that are currently blocking upgrades
        applications = applications.byIncreasingDeployedVersion(); // start with lowest versions
        applications = applications.first(numberOfApplicationsToUpgrade()); // throttle upgrades
        for (Application application : applications.asList()) {
            try {
                controller().applications().deploymentTrigger().triggerChange(application.id(), Change.of(version));
            } catch (IllegalArgumentException e) {
                log.log(Level.INFO, "Could not trigger change: " + Exceptions.toMessageString(e));
            }
        }
    }

    private void cancelUpgradesOf(ApplicationList applications, String reason) {
        if (applications.isEmpty()) return;
        log.info("Cancelling upgrading of " + applications.asList().size() + " applications: " + reason);
        for (Application application : applications.asList())
            controller().applications().deploymentTrigger().cancelChange(application.id(), true);
    }

    /** Returns the number of applications to upgrade in this run */
    private int numberOfApplicationsToUpgrade() {
        return Math.max(1, (int)(maintenanceInterval().getSeconds() * (upgradesPerMinute() / 60)));
    }

    /** Returns number upgrades per minute */
    public double upgradesPerMinute() {
        return curator.readUpgradesPerMinute();
    }

    /** Sets the number upgrades per minute */
    public void setUpgradesPerMinute(double n) {
        curator.writeUpgradesPerMinute(n);
    }

    /** Override confidence for given version. This will cause the computed confidence to be ignored */
    public void overrideConfidence(Version version, Confidence confidence) {
        Map<Version, Confidence> overrides = new LinkedHashMap<>(curator.readConfidenceOverrides());
        overrides.put(version, confidence);
        curator.writeConfidenceOverrides(overrides);
    }

    /** Returns all confidence overrides */
    public Map<Version, Confidence> confidenceOverrides() {
        return curator.readConfidenceOverrides();
    }

    /** Remove confidence override for given version */
    public void removeConfidenceOverride(Version version) {
        controller().removeConfidenceOverride(v -> v.equals(version));
    }
}
