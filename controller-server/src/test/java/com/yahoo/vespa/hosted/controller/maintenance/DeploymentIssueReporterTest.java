// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.canary;
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
            .region("us-west-1")
            .build();
    private final static ApplicationPackage canaryPackage = new ApplicationPackageBuilder()
            .region("us-west-1")
            .upgradePolicy("canary")
            .build();

    private DeploymentTester tester;
    private DeploymentIssueReporter reporter;
    private MockDeploymentIssues issues;

    @Before
    public void setup() {
        tester = new DeploymentTester();
        issues = new MockDeploymentIssues();
        reporter = new DeploymentIssueReporter(tester.controller(), issues, Duration.ofDays(1));
    }

    @Test
    public void testDeploymentFailureReporting() {
        tester.controllerTester().upgradeSystem(Version.fromString("6.2"));

        // Create and deploy one application for each of three tenants.
        var app1 = tester.newDeploymentContext("application1", "tenant1", "default");
        var app2 = tester.newDeploymentContext("application2", "tenant2", "default");
        var app3 = tester.newDeploymentContext("application3", "tenant3", "default");

        Contact contact = tester.controllerTester().serviceRegistry().contactRetrieverMock().contact();
        tester.controller().tenants().lockOrThrow(app1.instanceId().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));
        tester.controller().tenants().lockOrThrow(app2.instanceId().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));
        tester.controller().tenants().lockOrThrow(app3.instanceId().tenant(), LockedTenant.Athenz.class, tenant ->
                tester.controller().tenants().store(tenant.with(contact)));

        // NOTE: All maintenance should be idempotent within a small enough time interval, so maintain is called twice in succession throughout.

        // app 1 fails staging tests.
        app1.submit(applicationPackage).runJob(systemTest).timeOutConvergence(stagingTest);

        // app2 is successful, but will fail later.
        app2.submit(applicationPackage).deploy();

        // app 3 fails a production job.
        app3.submit(applicationPackage).runJob(systemTest).runJob(stagingTest).failDeployment(productionUsWest1);

        reporter.maintain();
        reporter.maintain();
        assertEquals("No deployments are detected as failing for a long time initially.", 0, issues.size());


        // Advance to where deployment issues should be detected.
        tester.clock().advance(maxFailureAge.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertTrue("One issue is produced for app1.", issues.isOpenFor(app1.application().id()));
        assertFalse("No issues are produced for app2.", issues.isOpenFor(app2.application().id()));
        assertTrue("One issue is produced for app3.", issues.isOpenFor(app3.application().id()));


        // app3 closes their issue prematurely; see that it is refiled.
        issues.closeFor(app3.application().id());
        assertFalse("No issue is open for app3.", issues.isOpenFor(app3.application().id()));

        reporter.maintain();
        reporter.maintain();
        assertTrue("Issue is re-filed for app3.", issues.isOpenFor(app3.application().id()));

        // Some time passes; tenant1 leaves her issue unattended, while tenant3 starts work and updates the issue.
        tester.clock().advance(maxInactivity.plus(maxFailureAge));
        issues.touchFor(app3.application().id());

        reporter.maintain();
        reporter.maintain();
        assertEquals("The issue for app1 is escalated once.", 1, issues.escalationLevelFor(app1.application().id()));


        // app3 fixes their problems, but the ticket for app3 is left open; see the resolved ticket is not escalated when another escalation period has passed.
        app3.runJob(productionUsWest1);
        tester.clock().advance(maxInactivity.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertFalse("We no longer have a platform issue.", issues.platformIssue());
        assertEquals("The issue for app1 is escalated once more.", 2, issues.escalationLevelFor(app1.application().id()));
        assertEquals("The issue for app3 is not escalated.", 0, issues.escalationLevelFor(app3.application().id()));


        // app3 now has a new failure past max failure age; see that a new issue is filed.
        app3.submit(applicationPackage).failDeployment(systemTest);
        tester.clock().advance(maxInactivity.plus(maxFailureAge));

        reporter.maintain();
        reporter.maintain();
        assertTrue("A new issue is filed for app3.", issues.isOpenFor(app3.application().id()));


        // app2 is changed to be a canary
        app2.submit(canaryPackage).deploy();
        assertEquals(canary, app2.application().deploymentSpec().requireInstance("default").upgradePolicy());
        assertEquals(Change.empty(), app2.instance().change());

        // Bump system version to upgrade canary app2.
        Version version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        assertEquals(version, tester.controller().readSystemVersion());

        app2.timeOutUpgrade(systemTest);
        tester.controllerTester().upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());

        assertFalse("We have no platform issues initially.", issues.platformIssue());
        reporter.maintain();
        reporter.maintain();
        assertFalse("We have no platform issue before the grace period is out for the failing canary.", issues.platformIssue());
        tester.clock().advance(upgradeGracePeriod.plus(upgradeGracePeriod));
        reporter.maintain();
        reporter.maintain();
        assertTrue("We get a platform issue when confidence is broken", issues.platformIssue());
        assertFalse("No deployment issue is filed for app2, which has a version upgrade failure.", issues.isOpenFor(app2.application().id()));

        app2.runJob(systemTest);
        tester.controllerTester().upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.low, tester.controller().readVersionStatus().systemVersion().get().confidence());
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
