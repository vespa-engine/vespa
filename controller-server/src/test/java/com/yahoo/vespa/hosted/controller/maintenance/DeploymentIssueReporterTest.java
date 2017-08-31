// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.Contacts.UserContact;
import com.yahoo.vespa.hosted.controller.api.integration.Issues;
import com.yahoo.vespa.hosted.controller.api.integration.Issues.IssueInfo;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.ContactsMock;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.PropertiesMock;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.Contacts.Category.admin;
import static com.yahoo.vespa.hosted.controller.api.integration.Contacts.Category.engineeringOwner;
import static com.yahoo.vespa.hosted.controller.api.integration.Issues.IssueInfo.Status.done;
import static com.yahoo.vespa.hosted.controller.api.integration.Issues.IssueInfo.Status.toDo;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionCorpUsEast1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxFailureAge;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.maxInactivityAge;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.terminalUser;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentIssueReporter.vespaOps;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jvenstad
 */
public class DeploymentIssueReporterTest {

    private final static ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .build();

    private DeploymentTester tester;
    private DeploymentIssueReporter reporter;
    private ContactsMock contacts;
    private PropertiesMock properties;
    private MockIssues issues;

    @Before
    public void setup() {
        tester = new DeploymentTester();
        contacts = new ContactsMock();
        properties = new PropertiesMock();
        issues = new MockIssues(tester.clock());
        reporter = new DeploymentIssueReporter(tester.controller(), contacts, properties, issues, Duration.ofMinutes(5), 
                                               new JobControl(new MockCuratorDb()));
    }

    private List<IssueInfo> openIssuesFor(Application application) {
        return issues.fetchSimilarTo(reporter.deploymentIssueFrom(tester.controller().applications().require(application.id())));
    }

    @Test
    public void testDeploymentFailureReporting() {
        // All applications deploy from unique SD projects.
        Long projectId1 = 10L;
        Long projectId2 = 20L;
        Long projectId3 = 30L;

        // Only the first two have propertyIds set now.
        Long propertyId1 = 1L;
        Long propertyId2 = 2L;

        // Create and deploy one application for each of three tenants.
        Application app1 = tester.createApplication("application1", "tenant1", projectId1, propertyId1);
        Application app2 = tester.createApplication("application2", "tenant2", projectId2, propertyId2);
        Application app3 = tester.createApplication("application3", "tenant3", projectId3, null);

        // And then we need lots of successful applications, so we won't assume we just have a faulty Vespa out.
        for (long i = 4; i <= 10; i++) {
            Application app = tester.createApplication("application" + i, "tenant" + i, 10 * i, i);
            tester.notifyJobCompletion(component, app, true);
            tester.deployAndNotify(app, applicationPackage, true, systemTest);
            tester.deployAndNotify(app, applicationPackage, true, stagingTest);
            tester.deployAndNotify(app, applicationPackage, true, productionCorpUsEast1);
        }

        // Both the first tenants belong to the same JIRA queue. (Not sure if this is possible, but let's test it anyway.
        String jiraQueue = "PROJECT";
        properties.addClassification(propertyId1, jiraQueue);
        properties.addClassification(propertyId1, jiraQueue);

        // Only tenant1 has contacts listed in opsDb.
        UserContact
                alice = new UserContact("alice", "Alice", admin),
                bob = new UserContact("bob", "Robert", engineeringOwner);
        contacts.addContact(propertyId1, Arrays.asList(alice, bob));

        // end of setup.

        // NOTE: All maintenance should be idempotent within a small enough time interval, so maintain is called twice in succession throughout.

        // app1 and app3 has one failure each.
        tester.notifyJobCompletion(component, app1, true);
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, false, stagingTest);

        tester.notifyJobCompletion(component, app2, true);
        tester.deployAndNotify(app2, applicationPackage, true, systemTest);
        tester.deployAndNotify(app2, applicationPackage, true, stagingTest);

        tester.notifyJobCompletion(component, app3, true);
        tester.deployAndNotify(app3, applicationPackage, true, systemTest);
        tester.deployAndNotify(app3, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app3, applicationPackage, false, productionCorpUsEast1);

        reporter.maintain();
        reporter.maintain();
        assertEquals("No deployments are detected as failing for a long time initially.", 0, issues.issues.size());


        // Advance to where deployment issues should be detected.
        tester.clock().advance(maxFailureAge.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertEquals("One issue is produced for app1.", 1, openIssuesFor(app1).size());
        assertEquals("No issues are produced for app2.", 0, openIssuesFor(app2).size());
        assertEquals("One issue is produced for app3.", 1, openIssuesFor(app3).size());
        assertTrue("The issue for app1 is stored in their JIRA queue.", openIssuesFor(app1).get(0).key().startsWith(jiraQueue));
        assertTrue("The issue for an application without propertyId is addressed to vespaOps.", openIssuesFor(app3).get(0).key().startsWith(vespaOps.queue()));


        // Verify idempotency of filing.
        reporter.maintain();
        reporter.maintain();
        assertEquals("No issues are re-filed when still open.", 2, issues.issues.size());


        // tenant3 closes their issue prematurely; see that we get a new filing.
        issues.complete(openIssuesFor(app3).get(0).id());
        assertEquals("The issue is removed (test of the tester, really...).", 0, openIssuesFor(app3).size());

        reporter.maintain();
        reporter.maintain();
        assertTrue("Issue is re-produced for app3, addressed correctly.", openIssuesFor(app3).get(0).key().startsWith(vespaOps.queue()));


        // Some time passes; tenant1 leaves her issue unattended, while tenant3 starts work and updates the issue.
        // app2 also has an intermittent failure; see that we detect this as a Vespa problem, and file an issue to ourselves.
        tester.deployAndNotify(app2, applicationPackage, false, productionCorpUsEast1);
        tester.clock().advance(maxInactivityAge.plus(maxFailureAge));
        issues.comment(openIssuesFor(app3).get(0).id(), "We are trying to fix it!");

        reporter.maintain();
        reporter.maintain();
        assertEquals("The issue for app1 is escalated once.", alice.username(), openIssuesFor(app1).get(0).assignee().get());


        reporter.maintain();
        reporter.maintain();
        assertEquals("We get an issue to vespaOps when more than 20% of applications have old failures.", 1,
                issues.fetchSimilarTo(reporter.manyFailingDeploymentsIssueFrom(Arrays.asList(
                        tester.controller().applications().get(app1.id()).get(),
                        tester.controller().applications().get(app2.id()).get(),
                        tester.controller().applications().get(app3.id()).get()))).size());
        assertEquals("No issue is filed for app2 while Vespa is considered broken.", 0, openIssuesFor(app2).size());


        // app3 fixes its problem, but the ticket is left open; see the resolved ticket is not escalated when another escalation period has passed.
        tester.deployAndNotify(app2, applicationPackage, true, productionCorpUsEast1);
        tester.deployAndNotify(app3, applicationPackage, true, productionCorpUsEast1);
        tester.clock().advance(maxInactivityAge.plus(Duration.ofDays(1)));

        reporter.maintain();
        reporter.maintain();
        assertEquals("The issue for app1 is escalated once more.", bob.username(), openIssuesFor(app1).get(0).assignee().get());
        assertEquals("The issue for app3 is still unassigned.", Optional.empty(), openIssuesFor(app3).get(0).assignee());


        // app1 still does nothing with their issue; see the terminal user gets it in the end.
        // app3 now has a new failure past max failure age; see that a new issue is filed.
        tester.notifyJobCompletion(component, app3, true);
        tester.deployAndNotify(app3, applicationPackage, true, systemTest);
        tester.deployAndNotify(app3, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app3, applicationPackage, false, productionCorpUsEast1);
        tester.clock().advance(maxInactivityAge.plus(maxFailureAge));

        reporter.maintain();
        reporter.maintain();
        assertEquals("The issue for app1 is escalated to the terminal user.", terminalUser.username(), openIssuesFor(app1).get(0).assignee().get());
        assertEquals("A new issue is filed for app3.", 2, openIssuesFor(app3).size());
    }

    class MockIssues implements Issues {

        final Map<String, Issue> issues = new HashMap<>();
        final Map<String, IssueInfo> metas = new HashMap<>();
        final Map<String, Long> counters = new HashMap<>();
        Clock clock;

        MockIssues(Clock clock) { this.clock = clock; }

        public void addWatcher(String jiraIssueId, String watcher) {
            touch(jiraIssueId);
        }

        public void reassign(String jiraIssueId, String assignee) {
            metas.compute(jiraIssueId, (__, jiraIssueMeta) ->
                    new IssueInfo(
                            jiraIssueId,
                            jiraIssueMeta.key(),
                            clock.instant(),
                            Optional.of(assignee),
                            jiraIssueMeta.status()));
        }

        public void comment(String jiraIssueId, String comment) {
            touch(jiraIssueId);
        }

        public void update(String jiraIssueId, String description) {
            issues.compute(jiraIssueId, (__, issue) ->
                    new Issue(issue.summary(), description, issue.classification().orElse(null)));
        }

        public String file(Issue issue) {
            String jiraIssueId = (issues.size() + 1L) + "";
            Long counter = counters.merge(issue.classification().get().queue(), 0L, (old, __) -> old + 1);
            String jiraIssueKey = issue.classification().get().queue() + '-' + counter;
            issues.put(jiraIssueId, issue);
            metas.put(jiraIssueId, new IssueInfo(jiraIssueId, jiraIssueKey, clock.instant(), null, toDo));
            return jiraIssueId;
        }

        public IssueInfo fetch(String jiraIssueId) {
            return metas.get(jiraIssueId);
        }

        public List<IssueInfo> fetchSimilarTo(Issue issue) {
            return issues.entrySet().stream()
                    .filter(entry -> entry.getValue().summary().equals(issue.summary()))
                    .map(Map.Entry::getKey)
                    .map(metas::get)
                    .filter(meta -> meta.status() != done)
                    .collect(Collectors.toList());
        }

        private void complete(String jiraIssueId) {
            metas.compute(jiraIssueId, (__, jiraIssueMeta) ->
                    new IssueInfo(
                            jiraIssueId,
                            jiraIssueMeta.key(),
                            clock.instant(),
                            jiraIssueMeta.assignee(),
                            done));
        }

        private void touch(String jiraIssueId) {
            metas.compute(jiraIssueId, (__, jiraIssueMeta) ->
                    new IssueInfo(
                            jiraIssueId,
                            jiraIssueMeta.key(),
                            clock.instant(),
                            jiraIssueMeta.assignee(),
                            jiraIssueMeta.status()));
        }

    }

}
