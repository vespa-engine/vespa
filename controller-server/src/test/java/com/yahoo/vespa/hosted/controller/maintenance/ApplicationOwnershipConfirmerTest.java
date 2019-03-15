// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

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
        Optional<Contact> contact = Optional.of(tester.controllerTester().contactRetriever().contact());
        TenantName property = tester.controllerTester().createTenant("property", "domain", 1L, contact);
        tester.createAndDeploy(property, "application", 1, "default");
        Supplier<Application> propertyApp = () -> tester.controller().applications().require(ApplicationId.from("property", "application", "default"));

        UserTenant user = UserTenant.create("by-user", contact);
        tester.controller().tenants().createUser(user);
        tester.createAndDeploy(user.name(), "application", 2, "default");
        Supplier<Application> userApp = () -> tester.controller().applications().require(ApplicationId.from("by-user", "application", "default"));

        assertFalse("No issue is initially stored for a new application.", propertyApp.get().ownershipIssueId().isPresent());
        assertFalse("No issue is initially stored for a new application.", userApp.get().ownershipIssueId().isPresent());
        assertFalse("No escalation has been attempted for a new application", issues.escalatedToContact || issues.escalatedToTerminator);

        // Set response from the issue mock, which will be obtained by the maintainer on issue filing.
        Optional<IssueId> issueId = Optional.of(IssueId.from("1"));
        issues.response = issueId;
        confirmer.maintain();

        assertFalse("No issue is stored for an application newer than 3 months.", propertyApp.get().ownershipIssueId().isPresent());
        assertFalse("No issue is stored for an application newer than 3 months.", userApp.get().ownershipIssueId().isPresent());

        tester.clock().advance(Duration.ofDays(91));
        confirmer.maintain();

        assertEquals("Confirmation issue has been filed for property owned application.", issueId, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue has been filed for user owned application.", issueId, userApp.get().ownershipIssueId());
        assertTrue(issues.escalatedToTerminator);
        assertTrue("Both applications have had their responses ensured.", issues.escalatedToContact && issues.escalatedToTerminator);

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();

        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, userApp.get().ownershipIssueId());

        // The user deletes all production deployments — see that the issue is forgotten.
        assertEquals("Confirmation issue for user is sitll open.", issueId, userApp.get().ownershipIssueId());
        tester.controller().applications().deactivate(userApp.get().id(), userApp.get().productionDeployments().keySet().stream().findAny().get());
        tester.controller().applications().deactivate(userApp.get().id(), userApp.get().productionDeployments().keySet().stream().findAny().get());
        assertTrue("No production deployments are listed for user.", userApp.get().productionDeployments().isEmpty());
        confirmer.maintain();

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        Optional<IssueId> issueId2 = Optional.of(IssueId.from("2"));
        issues.response = issueId2;
        confirmer.maintain();

        assertEquals("A new confirmation issue id is stored when something is returned to the maintainer.", issueId2, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue for application without production deployments has not been filed.", issueId, userApp.get().ownershipIssueId());

        assertFalse("No owner is stored for application", propertyApp.get().owner().isPresent());
        issues.owner = Optional.of(User.from("username"));
        confirmer.maintain();
        assertEquals("Owner has been added to application", propertyApp.get().owner().get().username(), "username");

    }

    private class MockOwnershipIssues implements OwnershipIssues {

        private Optional<IssueId> response;
        private boolean escalatedToContact = false;
        private boolean escalatedToTerminator = false;
        private Optional<User> owner = Optional.empty();

        @Override
        public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, User asignee, Contact contact) {
            return response;
        }

        @Override
        public void ensureResponse(IssueId issueId, Optional<Contact> contact) {
            if (contact.isPresent()) escalatedToContact = true;
            else escalatedToTerminator = true;
        }

        @Override
        public Optional<User> getConfirmedOwner(IssueId issueId) {
            return owner;
        }
    }

}
