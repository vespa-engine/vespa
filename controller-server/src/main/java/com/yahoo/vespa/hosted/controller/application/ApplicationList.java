// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
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

    // ----------------------------------- Filters

    /** Returns the subset of applications which have at least one production deployment */
    public ApplicationList withProductionDeployment() {
        return matching(application -> application.instances().values().stream()
                                                    .anyMatch(instance -> instance.productionDeployments().size() > 0));
    }

    /** Returns the subset of applications with at least one declared job in deployment spec. */
    public ApplicationList withJobs() {
        return matching(application -> application.deploymentSpec().steps().stream()
                                                  .anyMatch(step -> ! step.zones().isEmpty()));
    }

    /** Returns the subset of applications which have a project ID */
    public ApplicationList withProjectId() {
        return matching(application -> application.projectId().isPresent());
    }

    /** Returns the subset of application which have submitted a non-empty deployment spec. */
    public ApplicationList withDeploymentSpec() {
        return matching(application -> ! DeploymentSpec.empty.equals(application.deploymentSpec()));
    }

}
