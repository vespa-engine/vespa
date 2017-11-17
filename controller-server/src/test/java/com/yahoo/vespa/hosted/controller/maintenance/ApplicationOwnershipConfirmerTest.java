package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
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
 * @#author jvenstad
 */
public class ApplicationOwnershipConfirmerTest {

    private MockOwnershipIssues issues;
    private ApplicationOwnershipConfirmer confirmer;
    private ControllerTester tester;

    @Before
    public void setup() {
        tester = new ControllerTester();
        issues = new MockOwnershipIssues();
        confirmer = new ApplicationOwnershipConfirmer(tester.controller(), Duration.ofDays(1), new JobControl(new MockCuratorDb()), issues);
    }


    @Test
    public void testConfirmation() {
        TenantId property = tester.createTenant("tenant", "domain", 1L);
        ApplicationId propertyAppId = tester.createApplication(property, "application", "default", 1).id();
        Supplier<Application> propertyApp = () -> tester.controller().applications().require(propertyAppId);

        TenantId user = new TenantId("by-user");
        tester.controller().tenants().addTenant(Tenant.createUserTenant(new TenantId("by-user")), Optional.empty());
        assertTrue(tester.controller().tenants().tenant(user).isPresent());
        ApplicationId userAppId = tester.createApplication(user, "application", "default", 1).id();
        Supplier<Application> userApp = () -> tester.controller().applications().require(userAppId);

        assertFalse("No issue is initially stored for a new application.", propertyApp.get().ownershipIssueId().isPresent());
        assertFalse("No issue is initially stored for a new application.", userApp.get().ownershipIssueId().isPresent());
        assertFalse("No escalation has been attempted for a new application", issues.escalatedForProperty || issues.escalatedForUser);

        // Set response from the issue mock, which will be obtained by the maintainer on issue filing.
        Optional<IssueId> issueId = Optional.of(IssueId.from("1"));
        issues.response = issueId;
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("Confirmation issue has been filed for property owned application.", propertyApp.get().ownershipIssueId(), issueId);
        assertEquals("Confirmation issue has been filed for user owned application.", userApp.get().ownershipIssueId(), issueId);
        assertTrue("Both applications have had their responses ensured.", issues.escalatedForProperty && issues.escalatedForUser);

        // No new issue is created, so return empty now.
        issues.response = Optional.empty();
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("Confirmation issue reference is not updated when no issue id is returned.", propertyApp.get().ownershipIssueId(), issueId);

        // Time has passed, and a new confirmation issue is in order.
        Optional<IssueId> issueId2 = Optional.of(IssueId.from("2"));
        issues.response = issueId2;
        confirmer.maintain();
        confirmer.maintain();

        assertEquals("A new confirmation issue id is stored when something is returned to the maintainer.", propertyApp.get().ownershipIssueId(), issueId2);
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
