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
    IssueID fileIssue(ApplicationId applicationId);

    /**
     * Notifies those responsible for the Vespa platform that too many applications are failing.
     *
     * @param applicationIds IDs of all applications with failing deployments.
     * @return ID of the created issue.
     */
    IssueID fileIssue(Collection<ApplicationId> applicationIds);

    /**
     * @param issueID ID of the issue to escalate.
     */
    void escalateIssue(IssueID issueID);

    /**
     * @param issueID ID of the issue to examine.
     * @return Whether the given issue is under investigation.
     */
    boolean isOpen(IssueID issueID);

    /**
     * @param issueID IF of the issue to examine.
     * @return Whether the given issue is actively worked on.
     */
    boolean isActive(IssueID issueID);


    class IssueID {

        protected final String id;

        private IssueID(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }

        public static IssueID from(String value) {
            return new IssueID(value);
        }

    }

}
