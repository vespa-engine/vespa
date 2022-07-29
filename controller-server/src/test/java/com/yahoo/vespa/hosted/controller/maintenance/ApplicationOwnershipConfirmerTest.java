// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.appId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationOwnershipConfirmerTest {

    private MockOwnershipIssues issues;
    private ApplicationOwnershipConfirmer confirmer;
    private DeploymentTester tester;

    @BeforeEach
    public void setup() {
        tester = new DeploymentTester();
        issues = new MockOwnershipIssues();
        confirmer = new ApplicationOwnershipConfirmer(tester.controller(), Duration.ofDays(1), issues, 1);
    }

    @Test
    void testConfirmation() {
        Optional<Contact> contact = Optional.of(tester.controllerTester().serviceRegistry().contactRetrieverMock().contact());
        var app = tester.newDeploymentContext();
        tester.controller().tenants().lockOrThrow(appId.tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact.get())));
        app.submit().deploy();

        var appWithoutContact = tester.newDeploymentContext("other", "application", "default");
        appWithoutContact.submit().deploy();

        assertFalse(app.application().ownershipIssueId().isPresent(), "No issue is initially stored for a new application.");
        assertFalse(appWithoutContact.application().ownershipIssueId().isPresent(), "No issue is initially stored for a new application.");
        assertFalse(issues.escalated, "No escalation has been attempted for a new application");

        // Set response from the issue mock, which will be obtained by the maintainer on issue filing.
        Optional<IssueId> issueId = Optional.of(IssueId.from("1"));
        issues.response = issueId;
        confirmer.maintain();

        assertFalse(app.application().ownershipIssueId().isPresent(), "No issue is stored for an application newer than 3 months.");
        assertFalse(appWithoutContact.application().ownershipIssueId().isPresent(), "No issue is stored for an application newer than 3 months.");

        tester.clock().advance(Duration.ofDays(91));
        confirmer.maintain();

        assertEquals(issueId, app.application().ownershipIssueId(), "Confirmation issue has been filed for application with contact.");
        assertTrue(issues.escalated, "The confirmation issue response has been ensured.");
        assertEquals(Optional.empty(), appWithoutContact.application().ownershipIssueId(), "No confirmation issue has been filed for application without contact.");

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();

        assertEquals(issueId, app.application().ownershipIssueId(), "Confirmation issue reference is not updated when no issue id is returned.");

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        Optional<IssueId> issueId2 = Optional.of(IssueId.from("2"));
        issues.response = issueId2;
        confirmer.maintain();

        assertEquals(issueId2, app.application().ownershipIssueId(), "A new confirmation issue id is stored when something is returned to the maintainer.");

        assertFalse(app.application().owner().isPresent(), "No owner is stored for application");
        issues.owner = Optional.of(User.from("username"));
        confirmer.maintain();
        assertEquals(app.application().owner().get().username(), "username", "Owner has been added to application");

        // The app deletes all production deployments — see that the issue is forgotten.
        assertEquals(issueId2, app.application().ownershipIssueId(), "Confirmation issue for application is still open.");
        app.application().productionDeployments().values().stream().flatMap(List::stream)
                .forEach(deployment -> tester.controller().applications().deactivate(app.instanceId(), deployment.zone()));
        assertTrue(app.application().require(InstanceName.defaultName()).productionDeployments().isEmpty(), "No production deployments are listed for user.");
        confirmer.maintain();

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        issues.response = Optional.of(IssueId.from("3"));
        confirmer.maintain();

        assertEquals(issueId2, app.application().ownershipIssueId(), "Confirmation issue for application without production deployments has not been filed.");
    }

    private static class MockOwnershipIssues implements OwnershipIssues {

        private Optional<IssueId> response;
        private boolean escalated = false;
        private Optional<User> owner = Optional.empty();

        @Override
        public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationSummary summary, User assignee, Contact contact) {
            return response;
        }

        @Override
        public void ensureResponse(IssueId issueId, Optional<Contact> contact) {
            escalated = true;
        }

        @Override
        public Optional<User> getConfirmedOwner(IssueId issueId) {
            return owner;
        }
    }

}
