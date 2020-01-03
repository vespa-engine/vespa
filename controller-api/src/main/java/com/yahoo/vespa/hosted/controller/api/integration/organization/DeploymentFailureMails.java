// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.util.Collection;

/**
 * Used to create mails for different kinds of deployment failure.
 *
 * @author jonmv
 */
public class DeploymentFailureMails {

    private final ZoneRegistry registry;

    public DeploymentFailureMails(ZoneRegistry registry) {
        this.registry = registry;
    }

    public Mail outOfCapacity(RunId id, Collection<String> recipients) {
        return mail(id, recipients, " due to lack of capacity",
                    "as the zone does not have enough free capacity to " +
                    "accomodate the deployment. Please contact the Vespa team to request more!");
    }

    public Mail deploymentFailure(RunId id, Collection<String> recipients) {
        return mail(id, recipients, " deployment",
                    "and any previous deployment in the zone is unaffected. " +
                    "This is usually due to an invalid application configuration, or because " +
                    "of timeouts waiting for other deployments of the same application to finish.");
    }

    public Mail installationFailure(RunId id, Collection<String> recipients) {
        return mail(id, recipients, "installation",
                    "as nodes were not able to start the new Java containers. " +
                    "This is very often due to a misconfiguration of the components of an " +
                    "application, where one or more of these can not be instantiated.");
    }

    public Mail testFailure(RunId id, Collection<String> recipients) {
        return mail(id, recipients, "tests",
                    "as one or more verification tests against the deployment failed.");
    }

    public Mail systemError(RunId id, Collection<String> recipients) {
        return mail(id, recipients, "due to system error",
                    "as something in the framework went wrong. Such errors are " +
                    "usually transient. Please contact the Vespa team if the problem persists!");
    }

    private Mail mail(RunId id, Collection<String> recipients, String summaryDetail, String messageDetail) {
        return new Mail(recipients,
                        String.format("Vespa application %s: %s failing %s",
                                      id.application(),
                                      jobToString(id.type()),
                                      summaryDetail),
                        String.format("%s for the Vespa application '%s' just failed, %s\n" +
                                      "Details about the job can be viewed at %s.\n" +
                                      "If you require further assistance, please contact the Vespa team at %s.",
                                      jobToString(id.type()),
                                      id.application(),
                                      messageDetail,
                                      registry.dashboardUrl(id),
                                      registry.supportUrl()));
    }

    private String jobToString(JobType type) {
        if (type == JobType.systemTest)
            return "System test";
        if (type == JobType.stagingTest)
            return "Staging test";
        return "Deployment to " + type.zone(registry.system()).region();
    }

}
