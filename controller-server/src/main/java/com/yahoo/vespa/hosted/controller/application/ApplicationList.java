// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A list of applications which can be filtered in various ways.
 *
 * @author jonmv
 */
public class ApplicationList extends AbstractFilteringList<Application, ApplicationList> {

    private ApplicationList(Collection<? extends Application> applications, boolean negate) {
        super(applications, negate, ApplicationList::new);
    }

    // ----------------------------------- Factories

    public static ApplicationList from(Collection<? extends Application> applications) {
        return new ApplicationList(applications, false);
    }

    public static ApplicationList from(Collection<ApplicationId> ids, ApplicationController applications) {
        return from(ids.stream()
                       .map(TenantAndApplicationId::from)
                       .distinct()
                       .map(applications::requireApplication)
                       .collect(Collectors.toUnmodifiableList()));
    }

    // ----------------------------------- Accessors

    /** Returns the ids of the applications in this as an immutable list */
    public List<TenantAndApplicationId> idList() {
        return mapToList(Application::id);
    }

    // ----------------------------------- Filters

    /** Returns the subset of applications which have at least one production deployment */
    public ApplicationList withProductionDeployment() {
        return matching(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> instance.productionDeployments().size() > 0));
    }

    /** Returns the subset of applications which have at least one deployment on a lower version than the given one */
    public ApplicationList onLowerVersionThan(Version version) {
        return matching(application -> application.instances().values().stream()
                                                    .flatMap(instance -> instance.productionDeployments().values().stream())
                                                    .anyMatch(deployment -> deployment.version().isBefore(version)));
    }

    /** Returns the subset of applications which have a project ID */
    public ApplicationList withProjectId() {
        return matching(application -> application.projectId().isPresent());
    }

    /** Returns the subset of applications that are allowed to upgrade at the given time */
    public ApplicationList canUpgradeAt(Instant instant) {
        return matching(application -> application.deploymentSpec().instances().stream()
                                                    .allMatch(instance -> instance.canUpgradeAt(instant)));
    }

    /** Returns the subset of applications that have at least one assigned rotation */
    public ApplicationList hasRotation() {
        return matching(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> ! instance.rotations().isEmpty()));
    }

    /**
     * Returns the subset of applications that hasn't pinned to an an earlier major version than the given one.
     *
     * @param targetMajorVersion the target major version which applications returned allows upgrading to
     * @param defaultMajorVersion the default major version to assume for applications not specifying one
     */
    public ApplicationList allowMajorVersion(int targetMajorVersion, int defaultMajorVersion) {
        return matching(application -> targetMajorVersion <= application.deploymentSpec().majorVersion()
                                                                          .orElse(application.majorVersion()
                                                                                             .orElse(defaultMajorVersion)));
    }

    /** Returns the subset of application which have submitted a non-empty deployment spec. */
    public ApplicationList withDeploymentSpec() {
        return matching(application -> ! DeploymentSpec.empty.equals(application.deploymentSpec()));
    }

     // ----------------------------------- Sorting

    /**
     * Returns this list sorted by increasing deployed version.
     * If multiple versions are deployed the oldest is used.
     * Applications without any deployments are ordered first.
     */
    public ApplicationList byIncreasingDeployedVersion() {
        return sortedBy(Comparator.comparing(application -> application.oldestDeployedPlatform()
                                                                       .orElse(Version.emptyVersion)));
    }

}
