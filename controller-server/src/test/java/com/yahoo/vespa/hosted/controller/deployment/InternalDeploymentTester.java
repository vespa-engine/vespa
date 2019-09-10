// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunnerTest;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class InternalDeploymentTester {

    private static final String ATHENZ_DOMAIN = "domain";
    private static final String ATHENZ_SERVICE = "service";

    public static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from(ATHENZ_DOMAIN), AthenzService.from(ATHENZ_SERVICE))
            .upgradePolicy("default")
            .region("us-central-1")
            .parallel("us-west-1", "us-east-3")
            .emailRole("author")
            .emailAddress("b@a")
            .build();
    public static final ApplicationPackage publicCdApplicationPackage = new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from(ATHENZ_DOMAIN), AthenzService.from(ATHENZ_SERVICE))
            .upgradePolicy("default")
            .region("aws-us-east-1c")
            .emailRole("author")
            .emailAddress("b@a")
            .trust(generateCertificate())
            .build();
    public static final ApplicationId appId = ApplicationId.from("tenant", "application", "default");
    public static final TesterId testerId = TesterId.of(appId);
    public static final String athenzDomain = "domain";

    private final DeploymentTester tester;
    private final JobController jobs;
    private final RoutingGeneratorMock routing;
    private final MockTesterCloud cloud;
    private final JobRunner runner;

    public DeploymentTester tester() { return tester; }
    public JobController jobs() { return jobs; }
    public RoutingGeneratorMock routing() { return routing; }
    public MockTesterCloud cloud() { return cloud; }
    public JobRunner runner() { return runner; }
    public ConfigServerMock configServer() { return tester.configServer(); }
    public ApplicationController applications() { return tester.applications(); }
    public ManualClock clock() { return tester.clock(); }
    public Application app() { return tester.application(appId); }

    public InternalDeploymentTester() {
        tester = new DeploymentTester();
        tester.controllerTester().createApplication(tester.controllerTester().createTenant(appId.tenant().value(), athenzDomain, 1L),
                                                    appId.application().value(),
                                                    "default",
                                                    1);
        jobs = tester.controller().jobController();
        routing = tester.controllerTester().serviceRegistry().routingGeneratorMock();
        cloud = (MockTesterCloud) tester.controller().jobController().cloud();
        runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                               JobRunnerTest.inThreadExecutor(), new InternalStepRunner(tester.controller()));
        routing.putEndpoints(new DeploymentId(null, null), Collections.emptyList()); // Turn off default behaviour for the mock.

        // Get deployment job logs to stderr.
        Logger.getLogger(InternalStepRunner.class.getName()).setLevel(LogLevel.DEBUG);
        Logger.getLogger("").setLevel(LogLevel.DEBUG);
        tester.controllerTester().configureDefaultLogHandler(handler -> handler.setLevel(LogLevel.DEBUG));

        // Mock Athenz domain to allow launch of service
        AthenzDbMock.Domain domain = tester.controllerTester().athenzDb().getOrCreateDomain(new com.yahoo.vespa.athenz.api.AthenzDomain(ATHENZ_DOMAIN));
        domain.services.put(ATHENZ_SERVICE, new AthenzDbMock.Service(true));
    }

    /**
     * Submits a new application, and returns the version of the new submission.
     */
    public ApplicationVersion newSubmission() {
        return jobs.submit(appId, BuildJob.defaultSourceRevision, "a@b", 2,
                           tester.controller().system().isPublic() ? publicCdApplicationPackage : applicationPackage, new byte[0]);
    }

    /**
     * Sets a single endpoint in the routing mock; this matches that required for the tester.
     */
    public void setEndpoints(ApplicationId id, ZoneId zone) {
        routing.putEndpoints(new DeploymentId(id, zone),
                             Collections.singletonList(new RoutingEndpoint(String.format("https://%s--%s--%s.%s.%s.vespa:43",
                                                                                         id.instance().value(),
                                                                                         id.application().value(),
                                                                                         id.tenant().value(),
                                                                                         zone.region().value(),
                                                                                         zone.environment().value()),
                                                                           "host1",
                                                                           false,
                                                                           String.format("cluster1.%s.%s.%s.%s",
                                                                                         id.application().value(),
                                                                                         id.tenant().value(),
                                                                                         zone.region().value(),
                                                                                         zone.environment().value()))));
    }

    /**
     * Completely deploys a new submission and returns the new version.
     */
    public ApplicationVersion deployNewSubmission() {
        ApplicationVersion applicationVersion = newSubmission();

        assertFalse(app().deployments().values().stream()
                         .anyMatch(deployment -> deployment.applicationVersion().equals(applicationVersion)));
        assertEquals(applicationVersion, app().change().application().get());
        assertFalse(app().change().platform().isPresent());

        runJob(JobType.systemTest);
        runJob(JobType.stagingTest);
        runJob(JobType.productionUsCentral1);
        runJob(JobType.productionUsWest1);
        runJob(JobType.productionUsEast3);

        return applicationVersion;
    }

    /**
     * Completely deploys the given, new platform.
     */
    public void deployNewPlatform(Version version) {
        tester.upgradeSystem(version);
        assertFalse(app().deployments().values().stream()
                         .anyMatch(deployment -> deployment.version().equals(version)));
        assertEquals(version, app().change().platform().get());
        assertFalse(app().change().application().isPresent());

        runJob(JobType.systemTest);
        runJob(JobType.stagingTest);
        runJob(JobType.productionUsCentral1);
        runJob(JobType.productionUsWest1);
        runJob(JobType.productionUsEast3);
        assertTrue(app().productionDeployments().values().stream()
                        .allMatch(deployment -> deployment.version().equals(version)));
        assertTrue(tester.configServer().nodeRepository()
                         .list(JobType.productionAwsUsEast1a.zone(tester.controller().system()), appId).stream()
                         .allMatch(node -> node.currentVersion().equals(version)));
        assertTrue(tester.configServer().nodeRepository()
                         .list(JobType.productionUsEast3.zone(tester.controller().system()), appId).stream()
                         .allMatch(node -> node.currentVersion().equals(version)));
        assertTrue(tester.configServer().nodeRepository()
                         .list(JobType.productionUsEast3.zone(tester.controller().system()), appId).stream()
                         .allMatch(node -> node.currentVersion().equals(version)));
        assertFalse(app().change().hasTargets());
    }

    /**
     * Runs the whole of the given job, successfully.
     */
    public void runJob(JobType type) {
        tester.readyJobTrigger().maintain();
        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        assertFalse(run.hasFailed());
        assertNotSame(aborted, run.status());

        ZoneId zone = type.zone(tester.controller().system());
        DeploymentId deployment = new DeploymentId(appId, zone);

        // First steps are always deployments.
        runner.run();

        if (type == JobType.stagingTest) { // Do the initial deployment and installation of the real application.
            assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installInitialReal));
            tester.configServer().convergeServices(appId, zone);
            setEndpoints(appId, zone);
            run.versions().sourcePlatform().ifPresent(version -> tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), version));
            runner.run();
            assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installInitialReal));
        }

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installReal));
        tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), run.versions().targetPlatform());
        runner.run();
        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installReal));
        tester.configServer().convergeServices(appId, zone);
        runner.run();
        if (   ! (run.versions().sourceApplication().isPresent() && type.isProduction())
            &&   type != JobType.stagingTest) {
            assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installReal));
            setEndpoints(appId, zone);
        }
        runner.run();
        if (type.environment().isManuallyDeployed()) {
            assertEquals(Step.Status.succeeded, jobs.run(run.id()).get().steps().get(Step.installReal));
            assertTrue(jobs.run(run.id()).get().hasEnded());
            return;
        }
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installReal));

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installTester));
        tester.configServer().nodeRepository().doUpgrade(new DeploymentId(testerId.id(), zone), Optional.empty(), run.versions().targetPlatform());
        runner.run();
        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installTester));
        tester.configServer().convergeServices(testerId.id(), zone);
        runner.run();
        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installTester));
        setEndpoints(testerId.id(), zone);
        runner.run();
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installTester));

        // All installation is complete and endpoints are ready, so tests may begin.
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.startTests));

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.endTests));
        cloud.set(TesterCloud.Status.SUCCESS);
        runner.run();
        assertTrue(jobs.run(run.id()).get().hasEnded());
        assertFalse(jobs.run(run.id()).get().hasFailed());
        assertEquals(type.isProduction(), app().deployments().containsKey(zone));
        assertTrue(tester.configServer().nodeRepository().list(zone, testerId.id()).isEmpty());

        if ( ! app().deployments().containsKey(zone))
            routing.removeEndpoints(deployment);
        routing.removeEndpoints(new DeploymentId(testerId.id(), zone));
    }

    public RunId startSystemTestTests() {
        RunId id = newRun(JobType.systemTest);
        runner.run();
        tester.configServer().convergeServices(appId, JobType.systemTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(testerId.id(), JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(appId, JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(testerId.id(), JobType.systemTest.zone(tester.controller().system()));
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.endTests));
        return id;
    }

    /**
     * Creates and submits a new application, and then starts the job of the given type.
     */
    public RunId newRun(JobType type) {
        assertFalse(app().deploymentJobs().deployedInternally()); // Use this only once per test.
        newSubmission();
        tester.readyJobTrigger().maintain();

        if (type.isProduction()) {
            runJob(JobType.systemTest);
            runJob(JobType.stagingTest);
            tester.readyJobTrigger().maintain();
        }

        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        return run.id();
    }

    static X509Certificate generateCertificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal("CN=subject");
        return X509CertificateBuilder.fromKeypair(keyPair,
                                                  subject,
                                                  Instant.now(),
                                                  Instant.now().plusSeconds(1),
                                                  SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                  BigInteger.valueOf(1))
                                     .build();
    }

}
