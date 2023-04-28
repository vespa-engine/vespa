// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunnerTest;
import com.yahoo.vespa.hosted.controller.maintenance.OutstandingChangeDeployer;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
public class DeploymentTester {

    // Set a long interval so that maintainers never do scheduled runs during tests
    private static final Duration maintenanceInterval = Duration.ofDays(1);

    private static final String ATHENZ_DOMAIN = "domain";
    private static final String ATHENZ_SERVICE = "service";

    public static final TenantAndApplicationId appId = TenantAndApplicationId.from("tenant", "application");
    public static final ApplicationId instanceId = appId.defaultInstance();

    private final ControllerTester tester;
    private final JobController jobs;
    private final MockTesterCloud cloud;
    private final JobRunner runner;
    private final Upgrader upgrader;
    private final ReadyJobsTrigger readyJobsTrigger;
    private final OutstandingChangeDeployer outstandingChangeDeployer;

    public JobController jobs() { return jobs; }
    public MockTesterCloud cloud() { return cloud; }
    public JobRunner runner() { return runner; }
    public ConfigServerMock configServer() { return tester.configServer(); }
    public Controller controller() { return tester.controller(); }
    public DeploymentTrigger deploymentTrigger() { return applications().deploymentTrigger(); }
    public ControllerTester controllerTester() { return tester; }
    public Upgrader upgrader() { return upgrader; }
    public ApplicationController applications() { return tester.controller().applications(); }
    public ManualClock clock() { return tester.clock(); }
    public Application application() { return application(appId); }
    public Application application(TenantAndApplicationId id ) { return applications().requireApplication(id); }
    public Instance instance() { return instance(instanceId); }
    public Instance instance(ApplicationId id) { return applications().requireInstance(id); }
    public DeploymentStatusList deploymentStatuses() { return jobs.deploymentStatuses(ApplicationList.from(applications().asList())); }

    public DeploymentTester() {
        this(new ControllerTester());
    }

    public DeploymentTester(ControllerTester controllerTester) {
        tester = controllerTester;
        jobs = tester.controller().jobController();
        cloud = (MockTesterCloud) tester.controller().jobController().cloud();
        runner = new JobRunner(tester.controller(), maintenanceInterval, JobRunnerTest.inThreadExecutor(),
                               new InternalStepRunner(tester.controller()));
        upgrader = new Upgrader(tester.controller(), maintenanceInterval);
        upgrader.setUpgradesPerMinute(1); // Anything that makes it at least one for any maintenance period is fine.
        readyJobsTrigger = new ReadyJobsTrigger(tester.controller(), maintenanceInterval);
        outstandingChangeDeployer = new OutstandingChangeDeployer(tester.controller(), maintenanceInterval);

        // Get deployment job logs to stderr.
        Logger.getLogger("").setLevel(Level.FINE);
        Logger.getLogger(InternalStepRunner.class.getName()).setLevel(Level.FINE);
        tester.configureDefaultLogHandler(handler -> handler.setLevel(Level.FINE));

        // Mock Athenz domain to allow launch of service
        AthenzDbMock.Domain domain = tester.athenzDb().getOrCreateDomain(new com.yahoo.vespa.athenz.api.AthenzDomain(ATHENZ_DOMAIN));
        domain.services.put(ATHENZ_SERVICE, new AthenzDbMock.Service(true));
    }

    public ReadyJobsTrigger readyJobsTrigger() {
        return readyJobsTrigger;
    }

    public OutstandingChangeDeployer outstandingChangeDeployer() { return outstandingChangeDeployer; }

    /** A tester with clock configured to a time when confidence can freely change */
    public DeploymentTester atMondayMorning() {
        return at(tester.clock().instant().atZone(ZoneOffset.UTC)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .withHour(5)
                        .toInstant());
    }

    public DeploymentTester at(Instant instant) {
        tester.clock().setInstant(instant);
        return this;
    }

    /** Create the deployment context for the default instance id  */
    public DeploymentContext newDeploymentContext() {
        return newDeploymentContext(instanceId);
    }

    /** Create a new deployment context for given application */
    public DeploymentContext newDeploymentContext(String tenantName, String applicationName, String instanceName) {
        return newDeploymentContext(ApplicationId.from(tenantName, applicationName, instanceName));
    }

    /** Create a new deployment context for given application */
    public DeploymentContext newDeploymentContext(ApplicationId instance) {
        return new DeploymentContext(instance, this);
    }

    /** Create a new application with given tenant and application name */
    public Application createApplication(String tenantName, String applicationName, String instanceName) {
        return newDeploymentContext(tenantName, applicationName, instanceName).application();
    }

    /** Aborts and finishes all running jobs. */
    public void abortAll() {
        triggerJobs();
        for (Run run : jobs.active()) {
            jobs.abort(run.id(), "DeploymentTester.abortAll", false);
            runner.advance(jobs.run(run.id()));
            assertTrue(jobs.run(run.id()).hasEnded());
        }
    }

    /** Triggers jobs until nothing more triggers, and returns the number of triggered jobs. */
    public int triggerJobs() {
        int triggered;
        int triggeredTotal = 0;
        do {
            triggered = (int) deploymentTrigger().triggerReadyJobs().triggered();
            triggeredTotal += triggered;
        } while (triggered > 0);
        return triggeredTotal;
    }

}
