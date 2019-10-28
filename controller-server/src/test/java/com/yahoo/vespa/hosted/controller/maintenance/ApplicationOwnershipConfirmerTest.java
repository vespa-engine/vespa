// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester.appId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationOwnershipConfirmerTest {

    private MockOwnershipIssues issues;
    private ApplicationOwnershipConfirmer confirmer;
    private InternalDeploymentTester tester;

    @Before
    public void setup() {
        tester = new InternalDeploymentTester();
        issues = new MockOwnershipIssues();
        confirmer = new ApplicationOwnershipConfirmer(tester.controller(), Duration.ofDays(1), new JobControl(new MockCuratorDb()), issues);
    }

    @Test
    public void testConfirmation() {
        Optional<Contact> contact = Optional.of(tester.controllerTester().serviceRegistry().contactRetrieverMock().contact());
        tester.controller().tenants().lockOrThrow(appId.tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact.get())));
        Supplier<Application> propertyApp = tester::application;
        tester.deployNewSubmission(tester.newSubmission());

        UserTenant user = UserTenant.create("by-user", contact);
        tester.controller().tenants().createUser(user);
        tester.createApplication(user.name().value(), "application", "default");
        TenantAndApplicationId userAppId = TenantAndApplicationId.from("by-user", "application");
        Supplier<Application> userApp = () -> tester.controller().applications().requireApplication(userAppId);
        tester.deployNewSubmission(userAppId, tester.newSubmission(userAppId, DeploymentContext.applicationPackage));

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
        assertTrue("Both applications have had their responses ensured.", issues.escalatedToContact);

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();

        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, userApp.get().ownershipIssueId());

        // The user deletes all production deployments — see that the issue is forgotten.
        assertEquals("Confirmation issue for user is still open.", issueId, userApp.get().ownershipIssueId());
        userApp.get().productionDeployments().values().stream().flatMap(List::stream)
               .forEach(deployment -> tester.controller().applications().deactivate(userAppId.defaultInstance(), deployment.zone()));
        assertTrue("No production deployments are listed for user.", userApp.get().require(InstanceName.defaultName()).productionDeployments().isEmpty());
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
        public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationSummary summary, User assignee, Contact contact) {
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
