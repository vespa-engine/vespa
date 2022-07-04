// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.application.Change;

import java.time.Instant;
import java.util.Collection;

/**
 * List for filtering deployment status of applications, for inspection and decision making.
 *
 * @author jonmv
 */
public class DeploymentStatusList extends AbstractFilteringList<DeploymentStatus, DeploymentStatusList> {

    private DeploymentStatusList(Collection<? extends DeploymentStatus> items, boolean negate) {
        super(items, negate, DeploymentStatusList::new);
    }

    public static DeploymentStatusList from(Collection<? extends DeploymentStatus> status) {
        return new DeploymentStatusList(status, false);
    }

    /** Returns the subset of applications which have changes left to deploy; blocked, or deploying */
    public DeploymentStatusList withChanges() {
        return matching(status -> status.application().productionInstances().values().stream()
                                        .anyMatch(instance -> instance.change().hasTargets() || status.outstandingChange(instance.name()).hasTargets()));
    }

    /** Returns the subset of applications which have been failing an upgrade to the given version since the given instant */
    public DeploymentStatusList failingUpgradeToVersionSince(Version version, Instant threshold) {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs -> failingUpgradeToVersionSince(jobs, version, threshold)));
    }

    /** Returns the subset of applications which have been failing an application change since the given instant */
    public DeploymentStatusList failingApplicationChangeSince(Instant threshold) {
        return matching(status -> status.instanceJobs().entrySet().stream()
                                        .anyMatch(jobs -> failingApplicationChangeSince(jobs.getValue(),
                                                                                        status.application().require(jobs.getKey().instance()).change(),
                                                                                        threshold)));
    }

    private static boolean failingUpgradeToVersionSince(JobList jobs, Version version, Instant threshold) {
        return ! jobs.not().failingApplicationChange()
                     .firstFailing().endedNoLaterThan(threshold)
                     .lastCompleted().on(version)
                     .isEmpty();
    }

    private static boolean failingApplicationChangeSince(JobList jobs, Change change, Instant threshold) {
        return change.revision().map(revision -> ! jobs.failingWithBrokenRevisionSince(revision, threshold).isEmpty()).orElse(false);
    }

}
