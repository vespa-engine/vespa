// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
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
 * @author jvenstad
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
        TenantId property = tester.controllerTester().createTenant("property", "domain", 1L);
        tester.createAndDeploy(property, "application", 1, "default");
        Supplier<Application> propertyApp = () -> tester.controller().applications().require(ApplicationId.from("property", "application", "default"));

        TenantId user = new TenantId("by-user");
        tester.controller().tenants().addTenant(Tenant.createUserTenant(user), Optional.empty());
        tester.createAndDeploy(user, "application", 2, "default");
        Supplier<Application> userApp = () -> tester.controller().applications().require(ApplicationId.from("by-user", "application", "default"));

        assertFalse("No issue is initially stored for a new application.", propertyApp.get().ownershipIssueId().isPresent());
        assertFalse("No issue is initially stored for a new application.", userApp.get().ownershipIssueId().isPresent());
        assertFalse("No escalation has been attempted for a new application", issues.escalatedForProperty || issues.escalatedForUser);

        // Set response from the issue mock, which will be obtained by the maintainer on issue filing.
        Optional<IssueId> issueId = Optional.of(IssueId.from("1"));
        issues.response = issueId;
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("Confirmation issue has been filed for property owned application.", issueId, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue has been filed for user owned application.", issueId, userApp.get().ownershipIssueId());
        assertTrue("Both applications have had their responses ensured.", issues.escalatedForProperty && issues.escalatedForUser);

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", issueId, userApp.get().ownershipIssueId());

        // The user deletes all production deployments — see that the issue is forgotten.
        assertEquals("Confirmation issue for user is sitll open.", issueId, userApp.get().ownershipIssueId());
        tester.controller().applications().deactivate(userApp.get(), userApp.get().productionDeployments().keySet().stream().findAny().get());
        tester.controller().applications().deactivate(userApp.get(), userApp.get().productionDeployments().keySet().stream().findAny().get());
        assertTrue("No production deployments are listed for user.", userApp.get().productionDeployments().isEmpty());
        confirmer.maintain();
        confirmer.maintain();

        // Time has passed, and a new confirmation issue is in order for the property which is still in production.
        Optional<IssueId> issueId2 = Optional.of(IssueId.from("2"));
        issues.response = issueId2;
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("A new confirmation issue id is stored when something is returned to the maintainer.", issueId2, propertyApp.get().ownershipIssueId());
        assertEquals("Confirmation issue for application without production deployments has not been filed.", issueId, userApp.get().ownershipIssueId());
    }

    private class MockOwnershipIssues implements OwnershipIssues {

        private Optional<IssueId> response;
        private boolean escalatedForProperty = false;
        private boolean escalatedForUser = false;

        @Override
        public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, PropertyId propertyId) {
            return response;
        }

        @Override
        public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, User owner) {
            return response;
        }

        @Override
        public void ensureResponse(IssueId issueId, Optional<PropertyId> propertyId) {
            if (propertyId.isPresent()) escalatedForProperty = true;
            else escalatedForUser = true;
        }

    }

}
