// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.permits.AthenzApplicationPermit;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 * @author mpolden
 */
public class ControllerTest {

    @Test
    public void testDeployment() {
        // Setup system
        DeploymentTester tester = new DeploymentTester();
        ApplicationController applications = tester.controller().applications();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // staging job - succeeding
        Version version1 = tester.defaultPlatformVersion();
        Application app1 = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        assertEquals("Application version is known from completion of initial job",
                     ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber),
                     tester.controller().applications().require(app1.id()).change().application().get());
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());

        ApplicationVersion applicationVersion = tester.controller().applications().require(app1.id()).change().application().get();
        assertFalse("Application version has been set during deployment", applicationVersion.isUnknown());
        assertStatus(JobStatus.initial(stagingTest)
                              .withTriggering(version1, applicationVersion, Optional.empty(),"", tester.clock().instant().truncatedTo(MILLIS))
                              .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS)), app1.id(), tester.controller());

        // Causes first deployment job to be triggered
        assertStatus(JobStatus.initial(productionUsWest1)
                              .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)), app1.id(), tester.controller());
        tester.clock().advance(Duration.ofSeconds(1));

        // production job (failing) after deployment
        tester.deploy(productionUsWest1, app1, applicationPackage);
        tester.deployAndNotify(app1, applicationPackage, false, productionUsWest1);
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());

        JobStatus expectedJobStatus = JobStatus.initial(productionUsWest1)
                                               .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)) // Triggered first without application version info
                                               .withCompletion(42, Optional.of(JobError.unknown), tester.clock().instant().truncatedTo(MILLIS))
                                               .withTriggering(version1,
                                                               applicationVersion,
                                                               Optional.of(tester.application(app1.id()).deployments().get(productionUsWest1.zone(main))),
                                                               "",
                                                               tester.clock().instant().truncatedTo(MILLIS)); // Re-triggering (due to failure) has application version info

        assertStatus(expectedJobStatus, app1.id(), tester.controller());

        // Simulate restart
        tester.restartController();

        applications = tester.controller().applications();

        assertNotNull(tester.controller().tenants().get(TenantName.from("tenant1")));
        assertNotNull(applications.get(ApplicationId.from(TenantName.from("tenant1"),
                                                          ApplicationName.from("application1"),
                                                          InstanceName.from("default"))));
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());


        tester.clock().advance(Duration.ofHours(1));

        // system and staging test job - succeeding
        tester.jobCompletion(component).application(app1).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        applicationVersion = tester.application("app1").change().application().get();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        assertStatus(JobStatus.initial(systemTest)
                              .withTriggering(version1, applicationVersion, Optional.of(tester.application(app1.id()).deployments().get(productionUsWest1.zone(main))), "", tester.clock().instant().truncatedTo(MILLIS))
                              .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS)),
                     app1.id(), tester.controller());
        tester.clock().advance(Duration.ofHours(1)); // Stop retrying
        tester.jobCompletion(productionUsWest1).application(app1).unsuccessful().submit();
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);

        // production job succeeding now
        expectedJobStatus = expectedJobStatus
                .withTriggering(version1, applicationVersion, Optional.of(tester.application(app1.id()).deployments().get(productionUsWest1.zone(main))), "", tester.clock().instant().truncatedTo(MILLIS))
                .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS));
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);
        assertStatus(expectedJobStatus, app1.id(), tester.controller());

        // causes triggering of next production job
        assertStatus(JobStatus.initial(productionUsEast3)
                              .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)),
                     app1.id(), tester.controller());
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);

        assertEquals(5, applications.get(app1.id()).get().deploymentJobs().jobStatus().size());

        // Production zone for which there is no JobType is not allowed.
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("deep-space-9")
                .build();
        try {
            tester.controller().jobController().submit(app1.id(), BuildJob.defaultSourceRevision, "a@b",
                                                       2, applicationPackage, new byte[0]);
            fail("Expected exception due to illegal deployment spec.");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Zone prod.deep-space-9 in deployment spec was not found in this system!", e.getMessage());
        }

        // prod zone removal is not allowed
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).nextBuildNumber().nextBuildNumber().uploadArtifact(applicationPackage).submit();
        try {
            tester.deploy(systemTest, app1, applicationPackage);
            fail("Expected exception due to illegal production deployment removal");
        }
        catch (IllegalArgumentException e) {
            assertEquals("deployment-removal: application 'tenant1.app1' is deployed in us-west-1, but does not include this zone in deployment.xml. " +
                         ValidationOverrides.toAllowMessage(ValidationId.deploymentRemoval),
                         e.getMessage());
        }
        assertNotNull("Zone was not removed",
                      applications.require(app1.id()).deployments().get(productionUsWest1.zone(main)));
        JobStatus jobStatus = applications.require(app1.id()).deploymentJobs().jobStatus().get(productionUsWest1);
        assertNotNull("Deployment job was not removed", jobStatus);
        assertEquals(42, jobStatus.lastCompleted().get().id());
        assertEquals("New change available", jobStatus.lastCompleted().get().reason());

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).nextBuildNumber(2).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        assertNull("Zone was removed",
                   applications.require(app1.id()).deployments().get(productionUsWest1.zone(main)));
        assertNull("Deployment job was removed", applications.require(app1.id()).deploymentJobs().jobStatus().get(productionUsWest1));
    }

    @Test
    public void testDeploymentApplicationVersion() {
        DeploymentTester tester = new DeploymentTester();
        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        SourceRevision source = new SourceRevision("repo", "master", "commit1");

        ApplicationVersion applicationVersion = ApplicationVersion.from(source, 101);
        runDeployment(tester, app.id(), applicationVersion, applicationPackage, source,101);
        assertEquals("Artifact is downloaded twice in staging and once for other zones", 5,
                     tester.artifactRepository().hits(app.id(), applicationVersion.id()));

        // Application is upgraded. This makes deployment orchestration pick the last successful application version in
        // zones which do not have permanent deployments, e.g. test and staging
        runUpgrade(tester, app.id(), applicationVersion);
    }

    @Test
    public void testGlobalRotations() {
        // Setup
        ControllerTester tester = new ControllerTester();
        ZoneId zone = ZoneId.from(Environment.defaultEnvironment(), RegionName.defaultName());
        ApplicationId app = ApplicationId.from("tenant", "app1", "default");
        DeploymentId deployment = new DeploymentId(app, zone);
        tester.routingGenerator().putEndpoints(deployment, List.of(
                new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream2"),
                new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream1"),
                new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false, "upstream3"),
                new RoutingEndpoint("http://global-endpoint.vespa.yahooapis.com:4080", "host1", true, "upstream1"),
                new RoutingEndpoint("http://alias-endpoint.vespa.yahooapis.com:4080", "host1", true, "upstream1")
        ));

        Supplier<Map<RoutingEndpoint, EndpointStatus>> rotationStatus = () -> tester.controller().applications().globalRotationStatus(deployment);
        Function<String, Optional<EndpointStatus>> findStatusByUpstream = (upstreamName) -> {
            return rotationStatus.get()
                                 .entrySet().stream()
                                 .filter(kv -> kv.getKey().upstreamName().equals(upstreamName))
                                 .findFirst()
                                 .map(Map.Entry::getValue);
        };

        // Check initial rotation status
        assertEquals(1, rotationStatus.get().size());
        assertEquals(findStatusByUpstream.apply("upstream1").get().getStatus(), EndpointStatus.Status.in);

        // Set the global rotations out of service
        EndpointStatus status = new EndpointStatus(EndpointStatus.Status.out, "unit-test", "Test", tester.clock().instant().getEpochSecond());
        tester.controller().applications().setGlobalRotationStatus(deployment, status);
        assertEquals(1, rotationStatus.get().size());
        assertEquals(findStatusByUpstream.apply("upstream1").get().getStatus(), EndpointStatus.Status.out);
        assertEquals("unit-test", findStatusByUpstream.apply("upstream1").get().getReason());

        // Deployment without a global endpoint
        tester.routingGenerator().putEndpoints(deployment, List.of(
                new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream2"),
                new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream1"),
                new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false, "upstream3")
        ));
        assertFalse("No global endpoint exists", findStatusByUpstream.apply("upstream1").isPresent());
        try {
            tester.controller().applications().setGlobalRotationStatus(deployment, status);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    public void testDnsAliasRegistration() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .build();

        Function<String, Optional<Record>> findCname = (name) -> tester.controllerTester().nameService()
                                                                       .findRecords(Record.Type.CNAME,
                                                                                    RecordName.from(name))
                                                                       .stream()
                                                                       .findFirst();

        tester.deployCompletely(application, applicationPackage);
        assertEquals(3, tester.controllerTester().nameService().records().size());

        Optional<Record> record = findCname.apply("app1--tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = findCname.apply("app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = findCname.apply("app1.tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());
    }

    @Test
    public void testUpdatesExistingDnsAlias() {
        DeploymentTester tester = new DeploymentTester();

        Function<String, Optional<Record>> findCname = (name) -> tester.controllerTester().nameService()
                                                                       .findRecords(Record.Type.CNAME,
                                                                                    RecordName.from(name))
                                                                       .stream()
                                                                       .findFirst();

        // Application 1 is deployed and deleted
        {
            Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                    .build();

            tester.deployCompletely(app1, applicationPackage);
            assertEquals(3, tester.controllerTester().nameService().records().size());

            Optional<Record> record = findCname.apply("app1--tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            record = findCname.apply("app1.tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            // Application is deleted and rotation is unassigned
            applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .allow(ValidationId.deploymentRemoval)
                    .build();
            tester.jobCompletion(component).application(app1).nextBuildNumber().uploadArtifact(applicationPackage).submit();
            tester.deployAndNotify(app1, applicationPackage, true, systemTest);
            tester.applications().deactivate(app1.id(), ZoneId.from(Environment.test, RegionName.from("us-east-1")));
            tester.applications().deactivate(app1.id(), ZoneId.from(Environment.staging, RegionName.from("us-east-3")));
            tester.applications().deleteApplication(app1.id(), tester.controllerTester().permitFor(app1.id()));
            try (RotationLock lock = tester.applications().rotationRepository().lock()) {
                assertTrue("Rotation is unassigned",
                           tester.applications().rotationRepository().availableRotations(lock)
                                 .containsKey(new RotationId("rotation-id-01")));
            }

            // Records remain
            record = findCname.apply("app1--tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());

            record = findCname.apply("app1--tenant1.global.vespa.oath.cloud");
            assertTrue(record.isPresent());

            record = findCname.apply("app1.tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
        }

        // Application 2 is deployed and assigned same rotation as application 1 had before deletion
        {
            Application app2 = tester.createApplication("app2", "tenant2", 2, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            tester.deployCompletely(app2, applicationPackage);
            assertEquals(6, tester.controllerTester().nameService().records().size());

            Optional<Record> record = findCname.apply("app2--tenant2.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("app2--tenant2.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            record = findCname.apply("app2--tenant2.global.vespa.oath.cloud");
            assertTrue(record.isPresent());
            assertEquals("app2--tenant2.global.vespa.oath.cloud", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            record = findCname.apply("app2.tenant2.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("app2.tenant2.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());
        }

        // Application 1 is recreated, deployed and assigned a new rotation
        {
            tester.buildService().clear();
            Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            tester.deployCompletely(app1, applicationPackage);
            app1 = tester.applications().require(app1.id());
            assertEquals("rotation-id-02", app1.rotation().get().asString());

            // Existing DNS records are updated to point to the newly assigned rotation
            assertEquals(6, tester.controllerTester().nameService().records().size());

            Optional<Record> record = findCname.apply("app1--tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("rotation-fqdn-02.", record.get().data().asString());

            record = findCname.apply("app1--tenant1.global.vespa.oath.cloud");
            assertTrue(record.isPresent());
            assertEquals("rotation-fqdn-02.", record.get().data().asString());

            record = findCname.apply("app1.tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isPresent());
            assertEquals("rotation-fqdn-02.", record.get().data().asString());
        }

    }

    @Test
    public void testIntegrationTestDeployment() {
        DeploymentTester tester = new DeploymentTester();
        Version six = Version.fromString("6.1");
        tester.upgradeSystem(six);
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneId.from("prod", "cd-us-central-1"));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .majorVersion(6)
                .region("cd-us-central-1")
                .build();

        // Create application
        Application app = tester.createApplication("app1", "tenant1", 1, 2L);

        // Direct deploy is allowed when deployDirectly is true
        ZoneId zone = ZoneId.from("prod", "cd-us-central-1");
        // Same options as used in our integration tests
        DeployOptions options = new DeployOptions(true, Optional.empty(), false,
                                                  false);
        tester.controller().applications().deploy(app.id(), zone, Optional.of(applicationPackage), options);

        assertTrue("Application deployed and activated",
                   tester.controllerTester().configServer().application(app.id()).get().activated());

        assertTrue("No job status added",
                   tester.applications().require(app.id()).deploymentJobs().jobStatus().isEmpty());

        Version seven = Version.fromString("7.2");
        tester.upgradeSystem(seven);
        tester.controller().applications().deploy(app.id(), zone, Optional.of(applicationPackage), options);
        assertEquals(six, tester.application(app.id()).deployments().get(zone).version());
    }

    @Test
    public void testDevDeployment() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.dev)
                .majorVersion(6)
                .region("us-east-1")
                .build();

        // Create application
        Application app = tester.createApplication("app1", "tenant1", 1, 2L);
        ZoneId zone = ZoneId.from("dev", "us-east-1");

        // Deploy
        tester.controller().applications().deploy(app.id(), zone, Optional.of(applicationPackage), DeployOptions.none());
        assertTrue("Application deployed and activated",
                   tester.controllerTester().configServer().application(app.id()).get().activated());
        assertTrue("No job status added",
                   tester.applications().require(app.id()).deploymentJobs().jobStatus().isEmpty());
        assertEquals("DeploymentSpec is not persisted", DeploymentSpec.empty, tester.applications().require(app.id()).deploymentSpec());
    }

    @Test
    public void testSuspension() {
        DeploymentTester tester = new DeploymentTester();
        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .environment(Environment.prod)
                                                        .region("us-west-1")
                                                        .region("us-east-3")
                                                        .build();
        SourceRevision source = new SourceRevision("repo", "master", "commit1");

        ApplicationVersion applicationVersion = ApplicationVersion.from(source, 101);
        runDeployment(tester, app.id(), applicationVersion, applicationPackage, source,101);

        DeploymentId deployment1 = new DeploymentId(app.id(), ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
        DeploymentId deployment2 = new DeploymentId(app.id(), ZoneId.from(Environment.prod, RegionName.from("us-east-3")));
        assertFalse(tester.configServer().isSuspended(deployment1));
        assertFalse(tester.configServer().isSuspended(deployment2));
        tester.configServer().setSuspended(deployment1, true);
        assertTrue(tester.configServer().isSuspended(deployment1));
        assertFalse(tester.configServer().isSuspended(deployment2));
    }

    // Application may already have been deleted, or deployment failed without response, test that deleting a
    // second time will not fail
    @Test
    public void testDeletingApplicationThatHasAlreadyBeenDeleted() {
        DeploymentTester tester = new DeploymentTester();
        Application app = tester.createApplication("app2", "tenant1", 1, 12L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .region("us-west-1")
                .build();

        ZoneId zone = ZoneId.from("prod", "us-west-1");
        tester.controller().applications().deploy(app.id(), zone, Optional.of(applicationPackage), DeployOptions.none());
        tester.controller().applications().deactivate(app.id(), ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
        tester.controller().applications().deactivate(app.id(), ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
    }

    @Test
    public void testDeployApplicationPackageWithApplicationDir() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build(true);
        tester.deployCompletely(application, applicationPackage);
    }

    private void runUpgrade(DeploymentTester tester, ApplicationId application, ApplicationVersion version) {
        Version next = Version.fromString("6.2");
        tester.upgradeSystem(next);
        runDeployment(tester, tester.applications().require(application), version, Optional.of(next), Optional.empty());
    }

    private void runDeployment(DeploymentTester tester, ApplicationId application, ApplicationVersion version,
                               ApplicationPackage applicationPackage, SourceRevision sourceRevision, long buildNumber) {
        Application app = tester.applications().require(application);
        tester.jobCompletion(component)
              .application(app)
              .buildNumber(buildNumber)
              .sourceRevision(sourceRevision)
              .uploadArtifact(applicationPackage)
              .submit();

        ApplicationVersion change = ApplicationVersion.from(sourceRevision, buildNumber);
        assertEquals(change.id(), tester.controller().applications()
                                        .require(application)
                                        .change().application().get().id());
        runDeployment(tester, app, version, Optional.empty(), Optional.of(applicationPackage));
    }

    private void assertStatus(JobStatus expectedStatus, ApplicationId id, Controller controller) {
        Application app = controller.applications().get(id).get();
        JobStatus existingStatus = app.deploymentJobs().jobStatus().get(expectedStatus.type());
        assertNotNull("Status of type " + expectedStatus.type() + " is present", existingStatus);
        assertEquals(expectedStatus, existingStatus);
    }

    private void runDeployment(DeploymentTester tester, Application app, ApplicationVersion version,
                               Optional<Version> upgrade, Optional<ApplicationPackage> applicationPackage) {
        Version vespaVersion = upgrade.orElseGet(tester::defaultPlatformVersion);

        // Deploy in test
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        JobStatus expected = JobStatus.initial(stagingTest)
                                      .withTriggering(vespaVersion, version, Optional.ofNullable(tester.application(app.id()).deployments().get(productionUsWest1.zone(main))), "",
                                                      tester.clock().instant().truncatedTo(MILLIS))
                                      .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS));
        assertStatus(expected, app.id(), tester.controller());

        // Deploy in production
        expected = JobStatus.initial(productionUsWest1)
                            .withTriggering(vespaVersion, version, Optional.ofNullable(tester.application(app.id()).deployments().get(productionUsWest1.zone(main))), "",
                                            tester.clock().instant().truncatedTo(MILLIS))
                            .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS));
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        assertStatus(expected, app.id(), tester.controller());

        expected = JobStatus.initial(productionUsEast3)
                            .withTriggering(vespaVersion, version, Optional.ofNullable(tester.application(app.id()).deployments().get(productionUsEast3.zone(main))), "",
                                            tester.clock().instant().truncatedTo(MILLIS))
                            .withCompletion(42, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS));
        tester.deployAndNotify(app, applicationPackage, true, productionUsEast3);
        assertStatus(expected, app.id(), tester.controller());

        // Verify deployed version
        app = tester.controller().applications().require(app.id());
        for (Deployment deployment : app.productionDeployments().values()) {
            assertEquals(version, deployment.applicationVersion());
            upgrade.ifPresent(v -> assertEquals(v, deployment.version()));
        }
    }

}
