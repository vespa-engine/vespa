// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A list of applications which can be filtered in various ways.
 * 
 * @author bratseth
 */
public class ApplicationList {

    private final ImmutableList<Application> list;

    private ApplicationList(List<Application> applications) {
        this.list = ImmutableList.copyOf(applications);
    }
    
    // ----------------------------------- Factories
    
    public static ApplicationList from(List<Application> applications) {
        return new ApplicationList(applications);
    }

    public static ApplicationList from(List<ApplicationId> ids, ApplicationController applications) {
        return listOf(ids.stream().map(applications::require));
    }

    // ----------------------------------- Accessors

    /** Returns the applications in this as an immutable list */
    public List<Application> asList() { return list; }

    public boolean isEmpty() { return list.isEmpty(); }

    public int size() { return list.size(); }

    // ----------------------------------- Filters

    /** Returns the subset of applications which is currently upgrading to the given version */
    public ApplicationList upgradingTo(Version version) {
        return listOf(list.stream().filter(application -> isUpgradingTo(version, application)));
    }

    /** Returns the subset of applications which is currently upgrading to a version lower than the given version */
    public ApplicationList upgradingToLowerThan(Version version) {
        return listOf(list.stream().filter(application -> isUpgradingToLowerThan(version, application)));
    }

    /** Returns the subset of applications which is currently not upgrading to the given version */
    public ApplicationList notUpgradingTo(Version version) {
        return listOf(list.stream().filter(application -> ! isUpgradingTo(version, application)));
    }

    /** Returns the subset of applications which is currently not deploying a new application revision */
    public ApplicationList notDeployingApplication() {
        return listOf(list.stream().filter(application -> ! isDeployingApplicationChange(application)));
    }

    /** Returns the subset of applications which currently does not have any failing jobs */
    public ApplicationList notFailing() {
        return listOf(list.stream().filter(application -> ! application.deploymentJobs().hasFailures()));
    }

    /** Returns the subset of applications which currently does not have any failing jobs on the given version */
    public ApplicationList notFailingOn(Version version) {
        return listOf(list.stream().filter(application -> ! failingOn(version, application)));
    }

    /** Returns the subset of applications which have at least one deployment */
    public ApplicationList hasDeployment() {
        return listOf(list.stream().filter(a -> !a.deployments().isEmpty()));
    }

    /** Returns the subset of applications which started failing after the given instant */
    public ApplicationList startedFailingAfter(Instant instant) {
        return listOf(list.stream().filter(application -> application.deploymentJobs().failingSince().isAfter(instant)));
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
                          .filter(a -> a.deployments().values().stream().anyMatch(d -> d.version().isBefore(version))));
    }

    /**
     * Returns the subset of applications which are not pull requests: 
     * Pull requests changes the application instance name to default-pr[pull-request-number]
     */
    public ApplicationList notPullRequest() {
        return listOf(list.stream().filter(a -> ! a.id().instance().value().startsWith("default-pr")));
    }

    /** Returns the subset of applications that are allowed to upgrade at the given time */
    public ApplicationList canUpgradeAt(Instant instant) {
        return listOf(list.stream().filter(a -> a.deploymentSpec().canUpgradeAt(instant)));
    }

     // ----------------------------------- Sorting

    /**
     * Returns this list sorted by increasing deployed version.
     * If multiple versions are deployed the oldest is used.
     * Applications without any deployments are ordered first.
     */
    public ApplicationList byIncreasingDeployedVersion() {
        return listOf(list.stream().sorted(Comparator.comparing(application -> application.deployedVersion().orElse(Version.emptyVersion))));
    }

    /** Returns the subset of applications which currently do not have any job in progress for the given change */
    public ApplicationList notRunningJobFor(Change.VersionChange change) {
        return listOf(list.stream().filter(a -> !hasRunningJob(a, change)));
    }

    // ----------------------------------- Internal helpers
    
    private static boolean isUpgradingTo(Version version, Application application) {
        if ( ! (application.deploying().isPresent()) ) return false;
        if ( ! (application.deploying().get() instanceof Change.VersionChange) ) return false;
        return ((Change.VersionChange)application.deploying().get()).version().equals(version);
    }

    private static boolean isUpgradingToLowerThan(Version version, Application application) {
        if ( ! application.deploying().isPresent()) return false;
        if ( ! (application.deploying().get() instanceof Change.VersionChange) ) return false;
        return ((Change.VersionChange)application.deploying().get()).version().isBefore(version);
    }

    private static boolean isDeployingApplicationChange(Application application) {
        if ( ! application.deploying().isPresent()) return false;
        return application.deploying().get() instanceof Change.ApplicationChange;
    }
    
    private static boolean failingOn(Version version, Application application) {
        for (JobStatus jobStatus : application.deploymentJobs().jobStatus().values())
            if ( ! jobStatus.isSuccess() && jobStatus.lastCompleted().get().version().equals(version)) return true;
        return false;
    }

    private static boolean hasRunningJob(Application application, Change.VersionChange change) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(JobStatus::inProgress)
                .filter(jobStatus -> jobStatus.lastTriggered().isPresent())
                .map(jobStatus -> jobStatus.lastTriggered().get())
                .anyMatch(jobRun -> jobRun.version().equals(change.version()));
    }
    
    /** Convenience converter from a stream to an ApplicationList */
    private static ApplicationList listOf(Stream<Application> applications) {
        ImmutableList.Builder<Application> b = new ImmutableList.Builder<>();
        applications.forEach(b::add);
        return new ApplicationList(b.build());
    }

}
