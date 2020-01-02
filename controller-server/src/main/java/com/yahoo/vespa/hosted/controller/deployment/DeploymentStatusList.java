// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    public ApplicationList asApplicationList() {
        return ApplicationList.from(mapToList(DeploymentStatus::application));
    }

    public DeploymentStatusList forApplications(Collection<TenantAndApplicationId> applications) {
        return matching(status -> applications.contains(status.application().id()));
    }

    public DeploymentStatusList withProductionDeployment() {
        return matching(status -> status.application().productionDeployments().values().stream()
                                        .anyMatch(deployments -> ! deployments.isEmpty()));
    }

    public DeploymentStatusList failing() {
        return matching(DeploymentStatus::hasFailures);
    }

    public DeploymentStatusList failingUpgrade() {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs -> ! jobs.failing().not().failingApplicationChange().isEmpty()));
    }

    /** Returns the subset of applications which are upgrading (to any version), not considering block windows. */
    public DeploymentStatusList upgrading() {
        return matching(status -> status.application().change().platform().isPresent());
    }

    /** Returns the subset of applications which are currently upgrading to the given version */
    public DeploymentStatusList upgradingTo(Version version) {
        return upgradingTo(List.of(version));
    }

    /** Returns the subset of applications which are currently upgrading to the given version */
    public DeploymentStatusList upgradingTo(Collection<Version> versions) {
        return matching(status -> versions.stream().anyMatch(version -> status.application().change().platform().equals(Optional.of(version))));
    }

    /** Returns the subset of applications which are not pinned to a certain Vespa version. */
    public DeploymentStatusList unpinned() {
        return matching(status -> ! status.application().change().isPinned());
    }

    /** Returns the subset of applications which has the given upgrade policy */
    // TODO jonmv: Make this instance based when instances are orchestrated, and deployments reported per instance.
    public DeploymentStatusList with(DeploymentSpec.UpgradePolicy policy) {
        return matching(status ->  status.application().deploymentSpec().instances().stream()
                                         .anyMatch(instance -> instance.upgradePolicy() == policy));
    }

    /** Returns the subset of applications which have changes left to deploy; blocked, or deploying */
    public DeploymentStatusList withChanges() {
        return matching(status -> status.application().change().hasTargets() || status.outstandingChange().hasTargets());
    }

    /** Returns the subset of applications which are currently deploying a change */
    public DeploymentStatusList deploying() {
        return matching(status -> status.application().change().hasTargets());
    }

    /** Returns the subset of applications which have been failing an upgrade to the given version since the given instant */
    public DeploymentStatusList failingUpgradeToVersionSince(Version version, Instant threshold) {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs -> failingUpgradeToVersionSince(jobs, version, threshold)));
    }

    /** Returns the subset of applications which have been failing an application change since the given instant */
    public DeploymentStatusList failingApplicationChangeSince(Instant threshold) {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs -> failingApplicationChangeSince(jobs, threshold)));
    }

    /** Returns the subset of applications which currently have failing jobs on the given version */
    public DeploymentStatusList failingOn(Version version) {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs -> failingOn(jobs, version)));
    }

    /** Returns the subset of applications which started failing on the given version */
    public DeploymentStatusList startedFailingOn(Version version) {
        return matching(status -> status.instanceJobs().values().stream()
                                        .anyMatch(jobs ->  ! jobs.firstFailing().on(version).isEmpty()));
    }

    private static boolean failingOn(JobList jobs, Version version) {
        return ! jobs.failing()
                     .lastCompleted().on(version)
                     .isEmpty();
    }

    private static boolean failingUpgradeToVersionSince(JobList jobs, Version version, Instant threshold) {
        return ! jobs.not().failingApplicationChange()
                     .firstFailing().endedBefore(threshold)
                     .lastCompleted().on(version)
                     .isEmpty();
    }

    private static boolean failingApplicationChangeSince(JobList jobs, Instant threshold) {
        return ! jobs.failingApplicationChange()
                     .firstFailing().endedBefore(threshold)
                     .isEmpty();
    }

}
