// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.util.Optional;

/**
 * Source and target versions for an application.
 *
 * @author jvenstad
 * @author mpolden
 */
public class Versions {

    private final Version targetPlatform;
    private final ApplicationVersion targetApplication;
    private final Optional<Version> sourcePlatform;
    private final Optional<ApplicationVersion> sourceApplication;

    public Versions(Version targetPlatform, ApplicationVersion targetApplication, Optional<Version> sourcePlatform,
                    Optional<ApplicationVersion> sourceApplication) {
        this.targetPlatform = targetPlatform;
        this.targetApplication = targetApplication;
        this.sourcePlatform = sourcePlatform;
        this.sourceApplication = sourceApplication;
    }

    /** Target platform version for this */
    public Version targetPlatform() {
        return targetPlatform;
    }

    /** Target application version for this */
    public ApplicationVersion targetApplication() {
        return targetApplication;
    }

    /** Source platform version for this */
    public Optional<Version> sourcePlatform() {
        return sourcePlatform;
    }

    /** Source application version for this */
    public Optional<ApplicationVersion> sourceApplication() {
        return sourceApplication;
    }

    /** Returns whether source versions are present and match those of the given job run */
    public boolean sourcesMatchIfPresent(JobStatus.JobRun jobRun) {
        return (!sourcePlatform.filter(version -> !version.equals(targetPlatform)).isPresent() ||
                sourcePlatform.equals(jobRun.sourcePlatform())) &&
               (!sourceApplication.filter(version -> !version.equals(targetApplication)).isPresent() ||
                sourceApplication.equals(jobRun.sourceApplication()));
    }

    public boolean targetsMatch(JobStatus.JobRun jobRun) {
        return targetPlatform.equals(jobRun.platform()) &&
               targetApplication.equals(jobRun.application());
    }

    @Override
    public String toString() {
        return String.format("platform %s%s, application %s%s",
                             sourcePlatform.filter(source -> !source.equals(targetPlatform))
                                           .map(source -> source + " -> ").orElse(""),
                             targetPlatform,
                             sourceApplication.filter(source -> !source.equals(targetApplication))
                                              .map(source -> source.id() + " -> ").orElse(""),
                             targetApplication.id());
    }

    /** Create versions using change contained in application */
    public static Versions from(Application application, Version defaultPlatformVersion) {
        return from(application.change(), application, Optional.empty(), defaultPlatformVersion);
    }

    /** Create versions using given change and application */
    public static Versions from(Change change, Application application, Optional<Deployment> deployment,
                                Version defaultPlatformVersion) {
        return new Versions(targetPlatform(application, change, deployment, defaultPlatformVersion),
                            targetApplication(application, change, deployment),
                            deployment.map(Deployment::version),
                            deployment.map(Deployment::applicationVersion));
    }

    private static Version targetPlatform(Application application, Change change, Optional<Deployment> deployment,
                                          Version defaultVersion) {
        return max(deployment.map(Deployment::version), change.platform())
                .orElse(application.oldestDeployedPlatform()
                                   .orElse(defaultVersion));
    }

    private static ApplicationVersion targetApplication(Application application, Change change,
                                                        Optional<Deployment> deployment) {
        return max(deployment.map(Deployment::applicationVersion), change.application())
                .orElse(application.oldestDeployedApplication()
                                   .orElse(application.deploymentJobs().jobStatus().get(JobType.component)
                                                      .lastSuccess()
                                                      .get()
                                                      .application()));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return ! o1.isPresent() ? o2 : ! o2.isPresent() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

}
