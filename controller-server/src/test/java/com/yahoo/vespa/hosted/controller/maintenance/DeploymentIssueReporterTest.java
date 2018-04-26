// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionCorpUsEast1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxFailureAge;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxInactivity;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.upgradeGracePeriod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jvenstad
 */
public class DeploymentIssueReporterTest {

    private final static ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .build();
    private final static ApplicationPackage canaryPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .upgradePolicy("canary")
            .build();


    private DeploymentTester tester;
    private DeploymentIssueReporter reporter;
    private MockDeploymentIssues issues;

    @Before
    public void setup() {
        tester = new DeploymentTester();
        issues = new MockDeploymentIssues();
        reporter = new DeploymentIssueReporter(tester.controller(), issues, Duration.ofDays(1), new JobControl(new MockCuratorDb()));
    }

    @Test
    public void testDeploymentFailureReporting() {
        // All applications deploy from unique build projects.
        Long projectId1 = 10L;
        Long projectId2 = 20L;
        Long projectId3 = 30L;

        Long propertyId1 = 1L;
        Long propertyId2 = 2L;
        Long propertyId3 = 3L;

        tester.upgradeSystem(Version.fromString("5.1"));

        // Create and deploy one application for each of three tenants.
        Application app1 = tester.createApplication("application1", "tenant1", projectId1, propertyId1);
        Application app2 = tester.createApplication("application2", "tenant2", projectId2, propertyId2);
        Application app3 = tester.createApplication("application3", "tenant3", projectId3, propertyId3);

        // NOTE: All maintenance should be idempotent within a small enough time interval, so maintain is called twice in succession throughout.

        // apps 1 and 3 have one failure each.
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, false, stagingTest);

        // app2 is successful, but will fail later.
        tester.deployCompletely(app2, canaryPackage);

        tester.jobCompletion(component).application(app3).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app3, applicationPackage, true, systemTest);
        tester.deployAndNotify(app3, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app3, applicationPackage, false, productionCorpUsEast1);

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
        tester.deployAndNotify(app3, applicationPackage, true, productionCorpUsEast1);
        tester.clock().advance(maxInactivity.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertFalse("We no longer have a platform issue.", issues.platformIssue());
        assertEquals("The issue for app1 is escalated once more.", 2, issues.escalationLevelFor(app1.id()));
        assertEquals("The issue for app3 is not escalated.", 0, issues.escalationLevelFor(app3.id()));


        // app3 now has a new failure past max failure age; see that a new issue is filed.
        tester.jobCompletion(component).application(app3).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app3, applicationPackage, false, systemTest);
        tester.clock().advance(maxInactivity.plus(maxFailureAge));

        reporter.maintain();
        reporter.maintain();
        assertTrue("A new issue is filed for app3.", issues.isOpenFor(app3.id()));


        // Bump system version to 5.2 to upgrade canary app2.
        Version version = Version.fromString("5.2");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        tester.readyJobTrigger().maintain();
        tester.completeUpgradeWithError(app2, version, canaryPackage, systemTest);
        tester.upgradeSystem(version);
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

        Map<ApplicationId, IssueId> applicationIssues = new HashMap<>();
        Map<IssueId, Integer> issueLevels = new HashMap<>();

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
            applicationIssues.put(applicationId, issueId);
            return issueId;
        }

        void closeFor(ApplicationId applicationId) {
            issueUpdates.remove(applicationIssues.remove(applicationId));
        }

        void touchFor(ApplicationId applicationId) {
            issueUpdates.put(applicationIssues.get(applicationId), tester.clock().instant());
        }

        boolean isOpenFor(ApplicationId applicationId) {
            return applicationIssues.containsKey(applicationId);
        }

        int escalationLevelFor(ApplicationId applicationId) {
            return issueLevels.getOrDefault(applicationIssues.get(applicationId), 0);
        }

        int size() {
            return issueUpdates.size();
        }

        boolean platformIssue() {
            return platformIssue.get();
        }

    }

}
