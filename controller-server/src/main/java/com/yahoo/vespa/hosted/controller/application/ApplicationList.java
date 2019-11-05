// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Instance;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A list of applications which can be filtered in various ways.
 *
 * @author jonmv
 */
public class ApplicationList {

    private final List<Application> list;

    private ApplicationList(List<Application> applications) {
        this.list = applications;
    }

    // ----------------------------------- Factories

    public static ApplicationList from(Collection<Application> applications) {
        return new ApplicationList(List.copyOf(applications));
    }

    public static ApplicationList from(Collection<ApplicationId> ids, ApplicationController applications) {
        return new ApplicationList(ids.stream()
                                      .map(TenantAndApplicationId::from)
                                      .distinct()
                                      .map(applications::requireApplication)
                                      .collect(Collectors.toUnmodifiableList()));
    }

    // ----------------------------------- Accessors

    /** Returns the applications in this as an immutable list */
    public List<Application> asList() { return list; }

    /** Returns the ids of the applications in this as an immutable list */
    public List<TenantAndApplicationId> idList() { return list.stream().map(Application::id).collect(Collectors.toUnmodifiableList()); }

    public boolean isEmpty() { return list.isEmpty(); }

    public int size() { return list.size(); }

    // ----------------------------------- Filters

    /** Returns the subset of applications which are upgrading (to any version), not considering block windows. */
    public ApplicationList upgrading() {
        return filteredOn(application -> application.change().platform().isPresent());
    }

    /** Returns the subset of applications which are currently upgrading to the given version */
    public ApplicationList upgradingTo(Version version) {
        return filteredOn(application -> isUpgradingTo(version, application));
    }

    /** Returns the subset of applications which are not pinned to a certain Vespa version. */
    public ApplicationList unpinned() {
        return filteredOn(application -> ! application.change().isPinned());
    }

    /** Returns the subset of applications which are currently not upgrading to the given version */
    public ApplicationList notUpgradingTo(Version version) {
        return notUpgradingTo(Collections.singletonList(version));
    }

    public ApplicationList notFailingUpgrade() {
        return filteredOn(application -> application.instances().values().stream()
                                                    .allMatch(instance -> JobList.from(instance)
                                                                                 .failing()
                                                                                 .not().failingApplicationChange()
                                                                                 .isEmpty()));
    }

    /** Returns the subset of applications which are currently not upgrading to any of the given versions */
    public ApplicationList notUpgradingTo(Collection<Version> versions) {
        return filteredOn(application -> versions.stream().noneMatch(version -> isUpgradingTo(version, application)));
    }

    /**
     * Returns the subset of applications which are currently not upgrading to the given version,
     * or returns all if no version is specified
     */
    public ApplicationList notUpgradingTo(Optional<Version> version) {
        if (version.isEmpty()) return this;
        return notUpgradingTo(version.get());
    }

    /** Returns the subset of applications which have changes left to deploy; blocked, or deploying */
    public ApplicationList withChanges() {
        return filteredOn(application -> application.change().hasTargets() || application.outstandingChange().hasTargets());
    }

    /** Returns the subset of applications which are currently not deploying a change */
    public ApplicationList notDeploying() {
        return filteredOn(application -> ! application.change().hasTargets());
    }

    /** Returns the subset of applications which currently does not have any failing jobs */
    public ApplicationList notFailing() {
        return filteredOn(application -> application.instances().values().stream()
                                                    .noneMatch(instance -> instance.deploymentJobs().hasFailures()));
    }

    /** Returns the subset of applications which currently have failing jobs */
    public ApplicationList failing() {
        return filteredOn(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> instance.deploymentJobs().hasFailures()));
    }

    /** Returns the subset of applications which have been failing an upgrade to the given version since the given instant */
    public ApplicationList failingUpgradeToVersionSince(Version version, Instant threshold) {
        return filteredOn(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> failingUpgradeToVersionSince(instance, version, threshold)));
    }

    /** Returns the subset of applications which have been failing an application change since the given instant */
    public ApplicationList failingApplicationChangeSince(Instant threshold) {
        return filteredOn(application -> application.instances().values().stream()
                          .anyMatch(instance -> failingApplicationChangeSince(instance, threshold)));
    }

    /** Returns the subset of applications which currently does not have any failing jobs on the given version */
    public ApplicationList notFailingOn(Version version) {
        return filteredOn(application -> application.instances().values().stream()
                          .noneMatch(instance -> failingOn(version, instance)));
    }

    /** Returns the subset of applications which have at least one production deployment */
    public ApplicationList withProductionDeployment() {
        return filteredOn(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> instance.productionDeployments().size() > 0));
    }

    /** Returns the subset of applications which started failing on the given version */
    public ApplicationList startedFailingOn(Version version) {
        return filteredOn(application -> application.instances().values().stream()
                                                    .anyMatch(instance ->  ! JobList.from(instance).firstFailing().on(version).isEmpty()));
    }

    /** Returns the subset of applications which has the given upgrade policy */
    // TODO jonmv: Make this instance based when instances are orchestrated, and deployments reported per instance.
    public ApplicationList with(UpgradePolicy policy) {
        return filteredOn(application ->  application.deploymentSpec().instances().stream()
                                                     .anyMatch(instance -> instance.upgradePolicy() == policy));
    }

    /** Returns the subset of applications which does not have the given upgrade policy */
    // TODO jonmv: Make this instance based when instances are orchestrated, and deployments reported per instance.
    public ApplicationList without(UpgradePolicy policy) {
        return filteredOn(application ->  application.deploymentSpec().instances().stream()
                                                     .allMatch(instance -> instance.upgradePolicy() != policy));
    }

    /** Returns the subset of applications which have at least one deployment on a lower version than the given one */
    public ApplicationList onLowerVersionThan(Version version) {
        return filteredOn(application -> application.instances().values().stream()
                                                    .flatMap(instance -> instance.productionDeployments().values().stream())
                                                    .anyMatch(deployment -> deployment.version().isBefore(version)));
    }

    /** Returns the subset of applications which have a project ID */
    public ApplicationList withProjectId() {
        return filteredOn(application -> application.projectId().isPresent());
    }

    /** Returns the subset of applications that are allowed to upgrade at the given time */
    public ApplicationList canUpgradeAt(Instant instant) {
        return filteredOn(application -> application.deploymentSpec().instances().stream()
                                                    .allMatch(instance -> instance.canUpgradeAt(instant)));
    }

    /** Returns the subset of applications that have at least one assigned rotation */
    public ApplicationList hasRotation() {
        return filteredOn(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> ! instance.rotations().isEmpty()));
    }

    /**
     * Returns the subset of applications that hasn't pinned to an an earlier major version than the given one.
     *
     * @param targetMajorVersion the target major version which applications returned allows upgrading to
     * @param defaultMajorVersion the default major version to assume for applications not specifying one
     */
    public ApplicationList allowMajorVersion(int targetMajorVersion, int defaultMajorVersion) {
        return filteredOn(application -> targetMajorVersion <= application.deploymentSpec().majorVersion()
                                                                          .orElse(application.majorVersion()
                                                                                             .orElse(defaultMajorVersion)));
    }

    /** Returns the first n application in this (or all, if there are less than n). */
    public ApplicationList first(int n) {
        if (list.size() < n) return this;
        return new ApplicationList(list.subList(0, n));
    }

     // ----------------------------------- Sorting

    /**
     * Returns this list sorted by increasing deployed version.
     * If multiple versions are deployed the oldest is used.
     * Applications without any deployments are ordered first.
     */
    public ApplicationList byIncreasingDeployedVersion() {
        return new ApplicationList(list.stream()
                                       .sorted(Comparator.comparing(application -> application.oldestDeployedPlatform()
                                                                                              .orElse(Version.emptyVersion)))
                                       .collect(Collectors.toUnmodifiableList()));
    }

    // ----------------------------------- Internal helpers

    private static boolean isUpgradingTo(Version version, Application application) {
        return application.change().platform().equals(Optional.of(version));
    }

    private static boolean failingOn(Version version, Instance instance) {
        return ! JobList.from(instance)
                        .failing()
                        .lastCompleted().on(version)
                        .isEmpty();
    }

    private static boolean failingUpgradeToVersionSince(Instance instance, Version version, Instant threshold) {
        return ! JobList.from(instance)
                        .not().failingApplicationChange()
                        .firstFailing().before(threshold)
                        .lastCompleted().on(version)
                        .isEmpty();
    }

    private static boolean failingApplicationChangeSince(Instance instance, Instant threshold) {
        return ! JobList.from(instance)
                        .failingApplicationChange()
                        .firstFailing().before(threshold)
                        .isEmpty();
    }

    private ApplicationList filteredOn(Predicate<Application> condition) {
        return new ApplicationList(list.stream().filter(condition).collect(Collectors.toUnmodifiableList()));
    }

}
