// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.appId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationOwnershipConfirmerTest {

    private MockOwnershipIssues issues;
    private ApplicationOwnershipConfirmer confirmer;
    private DeploymentTester tester;

    @Before
    public void setup() {
        tester = new DeploymentTester();
        issues = new MockOwnershipIssues();
        confirmer = new ApplicationOwnershipConfirmer(tester.controller(), Duration.ofDays(1), new JobControl(new MockCuratorDb()), issues);
    }

    @Test
    public void testConfirmation() {
        Optional<Contact> contact = Optional.of(tester.controllerTester().serviceRegistry().contactRetrieverMock().contact());
        var app = tester.newDeploymentContext();
        tester.controller().tenants().lockOrThrow(appId.tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact.get())));
        app.submit().deploy();

        var appWithoutContact = tester.newDeploymentContext("other", "application", "default");
        appWithoutContact.submit().deploy();

        assertFalse("No issue is initially stored for a new application.", app.application().ownershipIssueId().isPresent());
        assertFalse("No issue is initially stored for a new application.", appWithoutContact.application().ownershipIssueId().isPresent());
        assertFalse("No escalation has been attempted for a new application", issues.escalated);

        // Set response from the issue mock, which will be obtained by the maintainer on issue filing.
        Optional<IssueId> issueId = Optional.of(IssueId.from("1"));
        issues.response = issueId;
        confirmer.maintain();

        assertFalse("No issue is stored for an application newer than 3 months.", app.application().ownershipIssueId().isPresent());
        assertFalse("No issue is stored for an application newer than 3 months.", appWithoutContact.application().ownershipIssueId().isPresent());

        tester.clock().advance(Duration.ofDays(91));
        confirmer.maintain();

        assertEquals("Confirmation issue has been filed for application with contact.", issueId, app.application().ownershipIssueId());
        assertTrue("The confirmation issue response has been ensured.", issues.escalated);
        assertEquals("No confirmation issue has been filed for application without contact.", Optional.empty(), appWithoutContact.application().ownershipIssueId());

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();

        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, app.application().ownershipIssueId());

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        Optional<IssueId> issueId2 = Optional.of(IssueId.from("2"));
        issues.response = issueId2;
        confirmer.maintain();

        assertEquals("A new confirmation issue id is stored when something is returned to the maintainer.", issueId2, app.application().ownershipIssueId());

        assertFalse("No owner is stored for application", app.application().owner().isPresent());
        issues.owner = Optional.of(User.from("username"));
        confirmer.maintain();
        assertEquals("Owner has been added to application", app.application().owner().get().username(), "username");

        // The app deletes all production deployments — see that the issue is forgotten.
        assertEquals("Confirmation issue for application is still open.", issueId2, app.application().ownershipIssueId());
        app.application().productionDeployments().values().stream().flatMap(List::stream)
           .forEach(deployment -> tester.controller().applications().deactivate(app.instanceId(), deployment.zone()));
        assertTrue("No production deployments are listed for user.", app.application().require(InstanceName.defaultName()).productionDeployments().isEmpty());
        confirmer.maintain();

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        Optional<IssueId> issueId3 = Optional.of(IssueId.from("3"));
        issues.response = issueId3;
        confirmer.maintain();

        assertEquals("Confirmation issue for application without production deployments has not been filed.", issueId2, app.application().ownershipIssueId());
    }

    private class MockOwnershipIssues implements OwnershipIssues {

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
