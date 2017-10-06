package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;

/**
 * Represents the people responsible for keeping Vespa up and running in a given organization, etc..
 *
 * @author jvenstad
 */
public interface AutonomousCarbonUnitsWhoFixApplications {

    /**
     * Notifies those responsible for the application with the given ID of failing deployments.
     *
     * @param applicationId ID of the application with failing deployments.
     * @return ID of the created issue.
     */
    IssueId fileIssue(ApplicationId applicationId);

    /**
     * Notifies those responsible for the Vespa platform that too many applications are failing.
     *
     * @param applicationIds IDs of all applications with failing deployments.
     * @return ID of the created issue.
     */
    IssueId fileIssue(Collection<ApplicationId> applicationIds);

    /**
     * @param issueId ID of the issue to escalate.
     */
    void escalateIssue(IssueId issueId);

    /**
     * @param issueId ID of the issue to examine.
     * @return Whether the given issue is under investigation.
     */
    boolean isOpen(IssueId issueId);

    /**
     * @param issueId IF of the issue to examine.
     * @return Whether the given issue is actively worked on.
     */
    boolean isActive(IssueId issueId);


    class IssueId {

        protected final String id;

        protected IssueId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }

        public static IssueId from(String value) {
            return new IssueId(value);
        }

    }

}
