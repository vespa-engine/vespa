// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A list of applications which can be filtered in various ways.
 * 
 * @author bratseth
 */
public class ApplicationList {

    private final ImmutableList<Application> list;

    private ApplicationList(Iterable<Application> applications) {
        this.list = ImmutableList.copyOf(applications);
    }
    
    // ----------------------------------- Factories
    
    public static ApplicationList from(Iterable<Application> applications) {
        return new ApplicationList(applications);
    }

    public static ApplicationList from(Collection<ApplicationId> ids, ApplicationController applications) {
        return listOf(ids.stream().map(applications::require));
    }

    // ----------------------------------- Accessors

    /** Returns the applications in this as an immutable list */
    public List<Application> asList() { return list; }

    /** Returns the ids of the applications in this as an immutable list */
    public List<ApplicationId> idList() { return ImmutableList.copyOf(list.stream().map(Application::id)::iterator); }

    public boolean isEmpty() { return list.isEmpty(); }

    public int size() { return list.size(); }

    // ----------------------------------- Filters

    /** Returns the subset of applications which are currently upgrading (to any version) */
    public ApplicationList upgrading() {
        return listOf(list.stream().filter(ApplicationList::isUpgrading));
    }

    /** Returns the subset of applications which are currently upgrading to the given version */
    public ApplicationList upgradingTo(Version version) {
        return listOf(list.stream().filter(application -> isUpgradingTo(version, application)));
    }

    /** Returns the subset of applications which are currently not upgrading to the given version */
    public ApplicationList notUpgradingTo(Version version) {
        return listOf(list.stream().filter(application -> ! isUpgradingTo(version, application)));
    }

    /** 
     * Returns the subset of applications which are currently not upgrading to the given version,
     * or returns all if no version is specified
     */
    public ApplicationList notUpgradingTo(Optional<Version> version) {
        if ( ! version.isPresent()) return this;
        return notUpgradingTo(version.get());
    }

    /** Returns the subset of applications which is currently not deploying a change */
    public ApplicationList notDeploying() {
        return listOf(list.stream().filter(application -> ! application.deploying().isPresent()));
    }

    /** Returns the subset of applications which currently does not have any failing jobs */
    public ApplicationList notFailing() {
        return listOf(list.stream().filter(application -> ! application.deploymentJobs().hasFailures()));
    }

    /** Returns the subset of applications which currently have failing jobs */
    public ApplicationList failing() {
        return listOf(list.stream().filter(application -> application.deploymentJobs().hasFailures()));
    }

    /** Returns the subset of applications which have been failing an upgrade to the given version since the given instant */
    public ApplicationList failingUpgradeToVersionSince(Version version, Instant threshold) {
        return listOf(list.stream().filter(application -> failingUpgradeToVersionSince(application, version, threshold)));
    }

    /** Returns the subset of applications which have been failing an application change since the given instant */
    public ApplicationList failingApplicationChangeSince(Instant threshold) {
        return listOf(list.stream().filter(application -> failingApplicationChangeSince(application, threshold)));
    }

    /** Returns the subset of applications which currently does not have any failing jobs on the given version */
    public ApplicationList notFailingOn(Version version) {
        return listOf(list.stream().filter(application -> ! failingOn(version, application)));
    }

    /** Returns the subset of applications which have at least one production deployment */
    public ApplicationList hasDeployment() {
        return listOf(list.stream().filter(a -> !a.productionDeployments().isEmpty()));
    }

    /** Returns the subset of applications which started failing on the given version */
    public ApplicationList startedFailingOn(Version version) {
        return listOf(list.stream().filter(application -> ! JobList.from(application).firstFailing().on(version).isEmpty()));
    }

    /** Returns the subset of applications which has the given upgrade policy */
    public ApplicationList with(UpgradePolicy policy) {
        return listOf(list.stream().filter(a ->  a.deploymentSpec().upgradePolicy() == policy));
    }

    /** Returns the subset of applications which does not have the given upgrade policy */
    public ApplicationList without(UpgradePolicy policy) {
        return listOf(list.stream().filter(a ->  a.deploymentSpec().upgradePolicy() != policy));
    }
    
    /** Returns the subset of applications which have at least one deployment on a lower version than the given one */
    public ApplicationList onLowerVersionThan(Version version) {
        return listOf(list.stream()
                          .filter(a -> a.productionDeployments().values().stream()
                                                                         .anyMatch(d -> d.version().isBefore(version))));
    }

    /**
     * Returns the subset of applications which are not pull requests: 
     * Pull requests changes the application instance name to (default-pr)?[pull-request-number]
     */
    public ApplicationList notPullRequest() {
        return listOf(list.stream().filter(a -> ! a.id().instance().value().matches("^(default-pr)?\\d+$")));
    }

    /** Returns the subset of applications which have at least one production deployment */
    public ApplicationList hasProductionDeployment() {
        return listOf(list.stream().filter(a -> ! a.productionDeployments().isEmpty()));
    }

    /** Returns the subset of applications that are allowed to upgrade at the given time */
    public ApplicationList canUpgradeAt(Instant instant) {
        return listOf(list.stream().filter(a -> a.deploymentSpec().canUpgradeAt(instant)));
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
        return listOf(list.stream().sorted(Comparator.comparing(application -> application.oldestDeployedVersion().orElse(Version.emptyVersion))));
    }

    // ----------------------------------- Internal helpers

    private static boolean isUpgrading(Application application) {
        if ( ! (application.deploying().isPresent()) ) return false;
        if ( ! (application.deploying().get() instanceof Change.VersionChange) ) return false;
        return true;
    }

    private static boolean isUpgradingTo(Version version, Application application) {
        if ( ! (application.deploying().isPresent()) ) return false;
        if ( ! (application.deploying().get() instanceof Change.VersionChange) ) return false;
        return ((Change.VersionChange)application.deploying().get()).version().equals(version);
    }

    private static boolean failingOn(Version version, Application application) {
        return ! JobList.from(application)
                .failing()
                .lastCompleted().on(version)
                .isEmpty();
    }

    private static boolean failingUpgradeToVersionSince(Application application, Version version, Instant threshold) {
        return ! JobList.from(application)
                .not().failingApplicationChange()
                .firstFailing().before(threshold)
                .lastCompleted().on(version)
                .isEmpty();
    }

    private static boolean failingApplicationChangeSince(Application application, Instant threshold) {
        return ! JobList.from(application)
                .failingApplicationChange()
                .firstFailing().before(threshold)
                .isEmpty();
    }

    /** Convenience converter from a stream to an ApplicationList */
    private static ApplicationList listOf(Stream<Application> applications) {
        return from(applications::iterator);
    }

}
