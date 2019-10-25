// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.canary;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxFailureAge;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxInactivity;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.upgradeGracePeriod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class DeploymentIssueReporterTest {

    private final static ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("us-west-1")
            .build();
    private final static ApplicationPackage canaryPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("us-west-1")
            .upgradePolicy("canary")
            .build();

    private InternalDeploymentTester tester;
    private DeploymentIssueReporter reporter;
    private MockDeploymentIssues issues;

    @Before
    public void setup() {
        tester = new InternalDeploymentTester();
        issues = new MockDeploymentIssues();
        reporter = new DeploymentIssueReporter(tester.controller(), issues, Duration.ofDays(1), new JobControl(new MockCuratorDb()));
    }

    @Test
    public void testDeploymentFailureReporting() {
        tester.controllerTester().upgradeSystem(Version.fromString("6.2"));

        /*
        tester.controllerTester().createTenant("tenant1", "domain1", 1L, contact);
        tester.controllerTester().createTenant("tenant2", "domain2", 1L, contact);
        tester.controllerTester().createTenant("tenant3", "domain3", 1L, contact);
        */

        // Create and deploy one application for each of three tenants.
        Application app1 = tester.createApplication("application1", "tenant1", "default");
        Application app2 = tester.createApplication("application2", "tenant2", "default");
        Application app3 = tester.createApplication("application3", "tenant3", "default");

        Contact contact = tester.controllerTester().serviceRegistry().contactRetrieverMock().contact();
        tester.controller().tenants().lockOrThrow(app1.id().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));
        tester.controller().tenants().lockOrThrow(app2.id().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));
        tester.controller().tenants().lockOrThrow(app3.id().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));

        // NOTE: All maintenance should be idempotent within a small enough time interval, so maintain is called twice in succession throughout.

        // app 1 fails staging tests.
        tester.newSubmission(app1.id(), applicationPackage);
        tester.runJob(app1.id().defaultInstance(), systemTest);
        tester.timeOutConvergence(app1.id().defaultInstance(), stagingTest);

        // app2 is successful, but will fail later.
        tester.deployNewSubmission(app2.id(), tester.newSubmission(app2.id(), applicationPackage));

        // app 3 fails a production job.
        tester.newSubmission(app3.id(), applicationPackage);
        tester.runJob(app3.id().defaultInstance(), systemTest);
        tester.runJob(app3.id().defaultInstance(), stagingTest);
        tester.failDeployment(app3.id().defaultInstance(), productionUsWest1);

        reporter.maintain();
        reporter.maintain();
        assertEquals("No deployments are detected as failing for a long time initially.", 0, issues.size());


        // Advance to where deployment issues should be detected.
        tester.clock().advance(maxFailureAge.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertTrue("One issue is produced for app1.", issues.isOpenFor(app1.id()));
        assertFalse("No issues are produced for app2.", issues.isOpenFor(app2.id()));
        assertTrue("One issue is produced for app3.", issues.isOpenFor(app3.id()));


        // app3 closes their issue prematurely; see that it is refiled.
        issues.closeFor(app3.id());
        assertFalse("No issue is open for app3.", issues.isOpenFor(app3.id()));

        reporter.maintain();
        reporter.maintain();
        assertTrue("Issue is re-filed for app3.", issues.isOpenFor(app3.id()));

        // Some time passes; tenant1 leaves her issue unattended, while tenant3 starts work and updates the issue.
        tester.clock().advance(maxInactivity.plus(maxFailureAge));
        issues.touchFor(app3.id());

        reporter.maintain();
        reporter.maintain();
        assertEquals("The issue for app1 is escalated once.", 1, issues.escalationLevelFor(app1.id()));


        // app3 fixes their problems, but the ticket for app3 is left open; see the resolved ticket is not escalated when another escalation period has passed.
        tester.runJob(app3.id().defaultInstance(), productionUsWest1);
        tester.clock().advance(maxInactivity.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertFalse("We no longer have a platform issue.", issues.platformIssue());
        assertEquals("The issue for app1 is escalated once more.", 2, issues.escalationLevelFor(app1.id()));
        assertEquals("The issue for app3 is not escalated.", 0, issues.escalationLevelFor(app3.id()));


        // app3 now has a new failure past max failure age; see that a new issue is filed.
        tester.newSubmission(app3.id(), applicationPackage);
        tester.failDeployment(app3.id().defaultInstance(), systemTest);
        tester.clock().advance(maxInactivity.plus(maxFailureAge));

        reporter.maintain();
        reporter.maintain();
        assertTrue("A new issue is filed for app3.", issues.isOpenFor(app3.id()));


        // App2 is changed to be a canary
        tester.deployNewSubmission(app2.id(), tester.newSubmission(app2.id(), canaryPackage));
        assertEquals(canary, tester.applications().requireApplication(app2.id()).deploymentSpec().requireInstance("default").upgradePolicy());
        assertEquals(Change.empty(), tester.applications().requireApplication(app2.id()).change());

        // Bump system version to upgrade canary app2.
        Version version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        tester.timeOutUpgrade(app2.id().defaultInstance(), systemTest);
        tester.controllerTester().upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());

        assertFalse("We have no platform issues initially.", issues.platformIssue());
        reporter.maintain();
        reporter.maintain();
        assertFalse("We have no platform issue before the grace period is out for the failing canary.", issues.platformIssue());
        tester.clock().advance(upgradeGracePeriod.plus(upgradeGracePeriod));
        reporter.maintain();
        reporter.maintain();
        assertTrue("We get a platform issue when confidence is broken", issues.platformIssue());
        assertFalse("No deployment issue is filed for app2, which has a version upgrade failure.", issues.isOpenFor(app2.id()));
    }


    class MockDeploymentIssues extends LoggingDeploymentIssues {

        private final Map<TenantAndApplicationId, IssueId> applicationIssues = new HashMap<>();
        private final Map<IssueId, Integer> issueLevels = new HashMap<>();

        MockDeploymentIssues() {
            super(tester.clock());
        }

        @Override
        protected void escalateIssue(IssueId issueId) {
            super.escalateIssue(issueId);
            issueLevels.merge(issueId, 1, Integer::sum);
        }

        @Override
        protected IssueId fileIssue(ApplicationId applicationId) {
            IssueId issueId = super.fileIssue(applicationId);
            applicationIssues.put(TenantAndApplicationId.from(applicationId), issueId);
            return issueId;
        }

        void closeFor(TenantAndApplicationId id) {
            issueUpdates.remove(applicationIssues.remove(id));
        }

        void touchFor(TenantAndApplicationId id) {
            issueUpdates.put(applicationIssues.get(id), tester.clock().instant());
        }

        boolean isOpenFor(TenantAndApplicationId id) {
            return applicationIssues.containsKey(id);
        }

        int escalationLevelFor(TenantAndApplicationId id) {
            return issueLevels.getOrDefault(applicationIssues.get(id), 0);
        }

        int size() {
            return issueUpdates.size();
        }

        boolean platformIssue() {
            return platformIssue.get();
        }

    }

}
