// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.Sets;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.SystemName.main;
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

    private final InternalDeploymentTester tester = new InternalDeploymentTester();

    @Test
    public void testDeployment() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // staging job - succeeding
        Version version1 = tester.configServer().initialVersion();
        var context = tester.deploymentContext();
        context.submit(applicationPackage);
        assertEquals("Application version is known from completion of initial job",
                     ApplicationVersion.from(DeploymentContext.defaultSourceRevision, 1, "a@b", new Version("6.1"), Instant.ofEpochSecond(1)),
                     context.application().change().application().get());
        context.runJob(systemTest);
        context.runJob(stagingTest);
        assertEquals(2, context.instance().deploymentJobs().jobStatus().size());

        ApplicationVersion applicationVersion = context.application().change().application().get();
        assertFalse("Application version has been set during deployment", applicationVersion.isUnknown());
        assertStatus(JobStatus.initial(stagingTest)
                              .withTriggering(version1, applicationVersion, Optional.empty(),"", tester.clock().instant().truncatedTo(MILLIS))
                              .withCompletion(1, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS)),
                     context.instanceId(),
                     tester.controller());

        tester.triggerJobs();
        // Causes first deployment job to be triggered
        assertStatus(JobStatus.initial(productionUsWest1)
                              .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)),
                     context.instanceId(),
                     tester.controller());
        tester.clock().advance(Duration.ofSeconds(1));

        // production job (failing) after deployment
        context.timeOutUpgrade(productionUsWest1);
        assertEquals(3, context.instance().deploymentJobs().jobStatus().size());
        tester.triggerJobs();

        JobStatus expectedJobStatus = JobStatus.initial(productionUsWest1)
                                               .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)) // Triggered first without application version info
                                               .withCompletion(1, Optional.of(JobError.unknown), tester.clock().instant().truncatedTo(MILLIS))
                                               .withTriggering(version1,
                                                               applicationVersion,
                                                               Optional.of(context.instance().deployments().get(productionUsWest1.zone(main))),
                                                               "",
                                                               tester.clock().instant().truncatedTo(MILLIS)); // Re-triggering (due to failure) has application version info

        assertStatus(expectedJobStatus, context.instanceId(), tester.controller());

        // Simulate restart
        tester.controllerTester().createNewController();

        assertNotNull(tester.controller().tenants().get(TenantName.from("tenant1")));
        assertNotNull(tester.controller().applications().requireInstance(context.instanceId()));
        assertEquals(3, context.instance().deploymentJobs().jobStatus().size());

        // system and staging test job - succeeding
        context.submit(applicationPackage);
        applicationVersion = context.application().change().application().get();
        context.runJob(systemTest);
        assertStatus(JobStatus.initial(systemTest)
                              .withTriggering(version1, applicationVersion, Optional.of(context.deployment(ZoneId.from("prod", "us-west-1"))), "", tester.clock().instant().truncatedTo(MILLIS))
                              .withCompletion(2, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS)),
                     context.instanceId(),
                     tester.controller());
        context.runJob(stagingTest);

        // production job succeeding now
        context.jobAborted(productionUsWest1);
        expectedJobStatus = expectedJobStatus
                .withTriggering(version1, applicationVersion, Optional.of(context.deployment(ZoneId.from("prod", "us-west-1"))), "", tester.clock().instant().truncatedTo(MILLIS))
                .withCompletion(3, Optional.empty(), tester.clock().instant().truncatedTo(MILLIS));
        context.runJob(productionUsWest1);
        assertStatus(expectedJobStatus, context.instanceId(), tester.controller());

        // causes triggering of next production job
        tester.triggerJobs();
        assertStatus(JobStatus.initial(productionUsEast3)
                              .withTriggering(version1, applicationVersion, Optional.empty(), "", tester.clock().instant().truncatedTo(MILLIS)),
                     context.instanceId(),
                     tester.controller());
        context.runJob(productionUsEast3);

        assertEquals(4, context.instance().deploymentJobs().jobStatus().size());

        // Production zone for which there is no JobType is not allowed.
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("deep-space-9")
                .build();
        try {
            context.submit(applicationPackage);
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
        try {
            assertTrue(context.instance().deployments().containsKey(ZoneId.from("prod", "us-west-1")));
            context.submit(applicationPackage);
            fail("Expected exception due to illegal production deployment removal");
        }
        catch (IllegalArgumentException e) {
            assertEquals("deployment-removal: application 'tenant.application' is deployed in us-west-1, but does not include this zone in deployment.xml. " +
                         ValidationOverrides.toAllowMessage(ValidationId.deploymentRemoval),
                         e.getMessage());
        }
        assertNotNull("Zone was not removed",
                      context.instance().deployments().get(productionUsWest1.zone(main)));
        JobStatus jobStatus = context.instance().deploymentJobs().jobStatus().get(productionUsWest1);
        assertNotNull("Deployment job was not removed", jobStatus);
        assertEquals(3, jobStatus.lastCompleted().get().id());
        assertEquals("New change available", jobStatus.lastCompleted().get().reason());

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        context.submit(applicationPackage);
        assertNull("Zone was removed",
                   context.instance().deployments().get(productionUsWest1.zone(main)));
        assertNull("Deployment job was removed", context.instance().deploymentJobs().jobStatus().get(productionUsWest1));
    }

    @Test
    public void testGlobalRotations() {
        // Setup
        ControllerTester tester = this.tester.controllerTester();
        ZoneId zone = ZoneId.from(Environment.defaultEnvironment(), RegionName.defaultName());
        ApplicationId app = ApplicationId.from("tenant", "app1", "default");
        DeploymentId deployment = new DeploymentId(app, zone);
        tester.serviceRegistry().routingGeneratorMock().putEndpoints(deployment, List.of(
                new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream2"),
                new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream1"),
                new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false, "upstream3"),
                new RoutingEndpoint("http://global-endpoint-2.vespa.yahooapis.com:4080", "host2", true, "upstream4"),
                new RoutingEndpoint("http://global-endpoint.vespa.yahooapis.com:4080", "host1", true, "upstream1"),
                new RoutingEndpoint("http://alias-endpoint.vespa.yahooapis.com:4080", "host1", true, "upstream1")
        ));

        Supplier<Map<RoutingEndpoint, EndpointStatus>> globalRotationStatus = () -> tester.controller().applications().globalRotationStatus(deployment);
        Supplier<List<EndpointStatus>> upstreamOneEndpoints = () -> {
            return globalRotationStatus.get()
                                       .entrySet().stream()
                                       .filter(kv -> kv.getKey().upstreamName().equals("upstream1"))
                                       .map(Map.Entry::getValue)
                                       .collect(Collectors.toList());
        };

        // Check initial rotation status
        assertEquals(3, globalRotationStatus.get().size());
        assertEquals(2, upstreamOneEndpoints.get().size());
        assertTrue("All upstreams are in", upstreamOneEndpoints.get().stream().allMatch(es -> es.getStatus() == EndpointStatus.Status.in));

        // Set the global rotations out of service
        EndpointStatus status = new EndpointStatus(EndpointStatus.Status.out, "unit-test", "Test", tester.clock().instant().getEpochSecond());
        tester.controller().applications().setGlobalRotationStatus(deployment, status);
        assertEquals(2, upstreamOneEndpoints.get().size());
        assertTrue("All upstreams are out", upstreamOneEndpoints.get().stream().allMatch(es -> es.getStatus() == EndpointStatus.Status.out));
        assertTrue("Reason is set", upstreamOneEndpoints.get().stream().allMatch(es -> es.getReason().equals("unit-test")));

        // Deployment without a global endpoint
        tester.serviceRegistry().routingGeneratorMock().putEndpoints(deployment, List.of(
                new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream2"),
                new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false, "upstream1"),
                new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false, "upstream3")
        ));
        try {
            tester.controller().applications().setGlobalRotationStatus(deployment, status);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    public void testDnsAliasRegistration() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "foo")
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .build();
        context.submit(applicationPackage).deploy();

        Collection<Deployment> deployments = context.instance().deployments().values();
        assertFalse(deployments.isEmpty());
        for (Deployment deployment : deployments) {
            assertEquals("Rotation names are passed to config server in " + deployment.zone(),
                         Set.of("rotation-id-01",
                                "app1--tenant1.global.vespa.oath.cloud"),
                         tester.configServer().rotationNames().get(context.deploymentIdIn(deployment.zone())));
        }
        context.flushDnsUpdates();

        assertEquals(1, tester.controllerTester().nameService().records().size());

        var record = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());
    }

    @Test
    public void testDnsAliasRegistrationLegacy() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .build();
        context.submit(applicationPackage).deploy();

        Collection<Deployment> deployments = context.instance().deployments().values();
        assertFalse(deployments.isEmpty());
        for (Deployment deployment : deployments) {
            assertEquals("Rotation names are passed to config server in " + deployment.zone(),
                    Set.of("rotation-id-01",
                            "app1--tenant1.global.vespa.oath.cloud",
                            "app1.tenant1.global.vespa.yahooapis.com",
                            "app1--tenant1.global.vespa.yahooapis.com"),
                    tester.configServer().rotationNames().get(context.deploymentIdIn(deployment.zone())));
        }
        context.flushDnsUpdates();
        assertEquals(3, tester.controllerTester().nameService().records().size());

        Optional<Record> record = tester.controllerTester().findCname("app1--tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = tester.controllerTester().findCname("app1.tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());
    }

    @Test
    public void testDnsAliasRegistrationWithEndpoints() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("foobar", "qrs", "us-west-1", "us-central-1")
                .endpoint("default", "qrs", "us-west-1", "us-central-1")
                .endpoint("all", "qrs")
                .endpoint("west", "qrs", "us-west-1")
                .region("us-west-1")
                .region("us-central-1")
                .build();
        context.submit(applicationPackage).deploy();

        Collection<Deployment> deployments = context.instance().deployments().values();
        assertFalse(deployments.isEmpty());

        var notWest = Set.of(
                "rotation-id-01", "foobar--app1--tenant1.global.vespa.oath.cloud",
                "rotation-id-02", "app1--tenant1.global.vespa.oath.cloud",
                "rotation-id-04", "all--app1--tenant1.global.vespa.oath.cloud"
        );
        var west = Sets.union(notWest, Set.of("rotation-id-03", "west--app1--tenant1.global.vespa.oath.cloud"));

        for (Deployment deployment : deployments) {
            assertEquals("Rotation names are passed to config server in " + deployment.zone(),
                    ZoneId.from("prod.us-west-1").equals(deployment.zone()) ? west : notWest,
                    tester.configServer().rotationNames().get(context.deploymentIdIn(deployment.zone())));
        }
        context.flushDnsUpdates();

        assertEquals(4, tester.controllerTester().nameService().records().size());

        var record1 = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record1.isPresent());
        assertEquals("app1--tenant1.global.vespa.oath.cloud", record1.get().name().asString());
        assertEquals("rotation-fqdn-04.", record1.get().data().asString());

        var record2 = tester.controllerTester().findCname("foobar--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record2.isPresent());
        assertEquals("foobar--app1--tenant1.global.vespa.oath.cloud", record2.get().name().asString());
        assertEquals("rotation-fqdn-01.", record2.get().data().asString());

        var record3 = tester.controllerTester().findCname("all--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record3.isPresent());
        assertEquals("all--app1--tenant1.global.vespa.oath.cloud", record3.get().name().asString());
        assertEquals("rotation-fqdn-02.", record3.get().data().asString());

        var record4 = tester.controllerTester().findCname("west--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record4.isPresent());
        assertEquals("west--app1--tenant1.global.vespa.oath.cloud", record4.get().name().asString());
        assertEquals("rotation-fqdn-03.", record4.get().data().asString());
    }

    @Test
    public void testDnsAliasRegistrationWithChangingEndpoints() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        var west = ZoneId.from("prod", "us-west-1");
        var central = ZoneId.from("prod", "us-central-1");
        var east = ZoneId.from("prod", "us-east-3");
        var buildNumber = BuildJob.defaultBuildNumber;

        // Application is deployed with endpoint pointing to 2/3 zones
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "qrs", west.region().value(), central.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        for (var zone : List.of(west, central)) {
            assertEquals(
                    "Zone " + zone + " is a member of global endpoint",
                    Set.of("rotation-id-01", "app1--tenant1.global.vespa.oath.cloud"),
                    tester.configServer().rotationNames().get(context.deploymentIdIn(zone))
            );
        }

        // Application is deployed with an additional endpoint
        ApplicationPackage applicationPackage2 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "qrs", west.region().value(), central.region().value())
                .endpoint("east", "qrs", east.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .build();
        context.submit(applicationPackage2).deploy();

        for (var zone : List.of(west, central)) {
            assertEquals(
                    "Zone " + zone + " is a member of global endpoint",
                    Set.of("rotation-id-01", "app1--tenant1.global.vespa.oath.cloud"),
                    tester.configServer().rotationNames().get(context.deploymentIdIn(zone))
            );
        }
        assertEquals(
                "Zone " + east + " is a member of global endpoint",
                Set.of("rotation-id-02", "east--app1--tenant1.global.vespa.oath.cloud"),
                tester.configServer().rotationNames().get(context.deploymentIdIn(east))
        );

        // Application is deployed with default endpoint pointing to 3/3 zones
        ApplicationPackage applicationPackage3 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "qrs", west.region().value(), central.region().value(), east.region().value())
                .endpoint("east", "qrs", east.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .build();
        context.submit(applicationPackage3).deploy();
        for (var zone : List.of(west, central, east)) {
            assertEquals(
                    "Zone " + zone + " is a member of global endpoint",
                    zone.equals(east)
                            ? Set.of("rotation-id-01", "app1--tenant1.global.vespa.oath.cloud",
                                     "rotation-id-02", "east--app1--tenant1.global.vespa.oath.cloud")
                            : Set.of("rotation-id-01", "app1--tenant1.global.vespa.oath.cloud"),
                    tester.configServer().rotationNames().get(context.deploymentIdIn(zone))
            );
        }

        // Region is removed from an endpoint without override
        ApplicationPackage applicationPackage4 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "qrs", west.region().value(), central.region().value())
                .endpoint("east", "qrs", east.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .build();
        try {
            context.submit(applicationPackage4);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("global-endpoint-change: application 'tenant1.app1' has endpoints " +
                         "[endpoint 'default' (cluster qrs) -> us-central-1, us-east-3, us-west-1, endpoint 'east' (cluster qrs) -> us-east-3], " +
                         "but does not include all of these in deployment.xml. Deploying given deployment.xml " +
                         "will remove [endpoint 'default' (cluster qrs) -> us-central-1, us-east-3, us-west-1] " +
                         "and add [endpoint 'default' (cluster qrs) -> us-central-1, us-west-1]. " +
                         ValidationOverrides.toAllowMessage(ValidationId.globalEndpointChange), e.getMessage());
        }

        // Entire endpoint is removed without override
        ApplicationPackage applicationPackage5 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("east", "qrs", east.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .build();
        try {
            context.submit(applicationPackage5);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("global-endpoint-change: application 'tenant1.app1' has endpoints " +
                         "[endpoint 'default' (cluster qrs) -> us-central-1, us-east-3, us-west-1, endpoint 'east' (cluster qrs) -> us-east-3], " +
                         "but does not include all of these in deployment.xml. Deploying given deployment.xml " +
                         "will remove [endpoint 'default' (cluster qrs) -> us-central-1, us-east-3, us-west-1]. " +
                         ValidationOverrides.toAllowMessage(ValidationId.globalEndpointChange), e.getMessage());
        }

        // ... override is added
        ApplicationPackage applicationPackage6 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("east", "qrs", east.region().value())
                .region(west.region().value())
                .region(central.region().value())
                .region(east.region().value())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context.submit(applicationPackage6);
    }

    @Test
    public void testUnassignRotations() {
        var context = tester.deploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .endpoint("default", "qrs", "us-west-1", "us-central-1")
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .build();
        context.submit(applicationPackage).deploy();

        ApplicationPackage applicationPackage2 = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .allow(ValidationId.globalEndpointChange)
                .build();

        context.submit(applicationPackage2).deploy();

        assertEquals(
                List.of(AssignedRotation.fromStrings("qrs", "default", "rotation-id-01", Set.of())),
                context.instance().rotations()
        );

        assertEquals(
                Set.of(),
                tester.configServer().rotationNames().get(context.deploymentIdIn(ZoneId.from("prod", "us-west-1")))
        );
    }

    @Test
    public void testUpdatesExistingDnsAlias() {
        // Application 1 is deployed and deleted
        {
            var context = tester.newDeploymentContext("tenant1", "app1", "default");
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .endpoint("default", "foo")
                    .region("us-west-1")
                    .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                    .build();

            context.submit(applicationPackage).deploy();
            assertEquals(1, tester.controllerTester().nameService().records().size());

            Optional<Record> record = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
            assertTrue(record.isPresent());
            assertEquals("app1--tenant1.global.vespa.oath.cloud", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            // Application is deleted and rotation is unassigned
            applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .allow(ValidationId.deploymentRemoval)
                    .allow(ValidationId.globalEndpointChange)
                    .build();
            context.submit(applicationPackage);
            tester.applications().deleteApplication(context.application().id(), tester.controllerTester().credentialsFor(context.application().id()));
            try (RotationLock lock = tester.applications().rotationRepository().lock()) {
                assertTrue("Rotation is unassigned",
                           tester.applications().rotationRepository().availableRotations(lock)
                                 .containsKey(new RotationId("rotation-id-01")));
            }
            context.flushDnsUpdates();

            // Records are removed
            record = tester.controllerTester().findCname("app1--tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isEmpty());

            record = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
            assertTrue(record.isEmpty());

            record = tester.controllerTester().findCname("app1.tenant1.global.vespa.yahooapis.com");
            assertTrue(record.isEmpty());
        }

        // Application 2 is deployed and assigned same rotation as application 1 had before deletion
        {
            var context = tester.newDeploymentContext("tenant2", "app2", "default");
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .endpoint("default", "foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            context.submit(applicationPackage).deploy();
            assertEquals(1, tester.controllerTester().nameService().records().size());

            var record = tester.controllerTester().findCname("app2--tenant2.global.vespa.oath.cloud");
            assertTrue(record.isPresent());
            assertEquals("app2--tenant2.global.vespa.oath.cloud", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());
        }

        // Application 1 is recreated, deployed and assigned a new rotation
        {
            var context = tester.newDeploymentContext("tenant1", "app1", "default");
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .endpoint("default", "foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            context.submit(applicationPackage).deploy();
            assertEquals("rotation-id-02", context.instance().rotations().get(0).rotationId().asString());

            // DNS records are created for the newly assigned rotation
            assertEquals(2, tester.controllerTester().nameService().records().size());

            var record1 = tester.controllerTester().findCname("app1--tenant1.global.vespa.oath.cloud");
            assertTrue(record1.isPresent());
            assertEquals("rotation-fqdn-02.", record1.get().data().asString());

            var record2 = tester.controllerTester().findCname("app2--tenant2.global.vespa.oath.cloud");
            assertTrue(record2.isPresent());
            assertEquals("rotation-fqdn-01.", record2.get().data().asString());
        }

    }

    @Test
    public void testIntegrationTestDeployment() {
        Version six = Version.fromString("6.1");
        tester.controllerTester().upgradeSystem(six);
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneApiMock.fromId("prod.cd-us-central-1"));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .majorVersion(6)
                .region("cd-us-central-1")
                .build();

        // Create application
        var context = tester.deploymentContext();

        // Direct deploy is allowed when deployDirectly is true
        ZoneId zone = ZoneId.from("prod", "cd-us-central-1");
        // Same options as used in our integration tests
        DeployOptions options = new DeployOptions(true, Optional.empty(), false,
                                                  false);
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), options);

        assertTrue("Application deployed and activated",
                   tester.configServer().application(context.instanceId(), zone).get().activated());

        assertTrue("No job status added",
                   context.instance().deploymentJobs().jobStatus().isEmpty());

        Version seven = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(seven);
        tester.upgrader().maintain();
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), options);
        assertEquals(six, context.instance().deployments().get(zone).version());
    }

    @Test
    public void testDevDeployment() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.dev)
                .majorVersion(6)
                .region("us-east-1")
                .build();

        // Create application
        var context = tester.deploymentContext();
        ZoneId zone = ZoneId.from("dev", "us-east-1");

        // Deploy
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), DeployOptions.none());
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context.instanceId(), zone).get().activated());
        assertTrue("No job status added",
                   context.instance().deploymentJobs().jobStatus().isEmpty());
        assertEquals("DeploymentSpec is not persisted", DeploymentSpec.empty, context.application().deploymentSpec());
    }

    @Test
    public void testSuspension() {
        var context = tester.deploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .environment(Environment.prod)
                                                        .region("us-west-1")
                                                        .region("us-east-3")
                                                        .build();
        SourceRevision source = new SourceRevision("repo", "master", "commit1");

        ApplicationVersion applicationVersion = ApplicationVersion.from(source, 101);
        context.submit(applicationPackage).deploy();

        DeploymentId deployment1 = context.deploymentIdIn(ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
        DeploymentId deployment2 = context.deploymentIdIn(ZoneId.from(Environment.prod, RegionName.from("us-east-3")));
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
        var context = tester.deploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .region("us-west-1")
                .build();

        ZoneId zone = ZoneId.from("prod", "us-west-1");
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), DeployOptions.none());
        tester.controller().applications().deactivate(context.instanceId(), ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
        tester.controller().applications().deactivate(context.instanceId(), ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
    }

    @Test
    public void testDeployApplicationPackageWithApplicationDir() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build(true);
        tester.deploymentContext().submit(applicationPackage);
    }

    @Test
    public void testDeployApplicationWithWarnings() {
        var context = tester.deploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        ZoneId zone = ZoneId.from("prod", "us-west-1");
        int warnings = 3;
        tester.configServer().generateWarnings(context.deploymentIdIn(zone), warnings);
        context.submit(applicationPackage).deploy();
        assertEquals(warnings, context.deployment(zone)
                                      .metrics().warnings().get(DeploymentMetrics.Warning.all).intValue());
    }

    @Test
    public void testDeploySelectivelyProvisionsCertificate() {
        ((InMemoryFlagSource) tester.controller().flagSource()).withBooleanFlag(Flags.PROVISION_APPLICATION_CERTIFICATE.id(), true);
        Function<Instance, Optional<ApplicationCertificate>> certificate = (application) -> tester.controller().curator().readApplicationCertificate(application.id());

        // Create app1
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var applicationPackage = new ApplicationPackageBuilder().environment(Environment.prod)
                                                                               .region("us-west-1")
                                                                               .build();
        // Deploy app1 in production
        context1.submit(applicationPackage).deploy();
        var cert = certificate.apply(context1.instance());
        assertTrue("Provisions certificate in " + Environment.prod, cert.isPresent());
        assertEquals(List.of(
                "vznqtz7a5ygwjkbhhj7ymxvlrekgt4l6g.vespa.oath.cloud",
                "app1.tenant1.global.vespa.oath.cloud",
                "*.app1.tenant1.global.vespa.oath.cloud",
                "app1.tenant1.us-east-3.vespa.oath.cloud",
                "*.app1.tenant1.us-east-3.vespa.oath.cloud",
                "app1.tenant1.us-west-1.vespa.oath.cloud",
                "*.app1.tenant1.us-west-1.vespa.oath.cloud",
                "app1.tenant1.us-central-1.vespa.oath.cloud",
                "*.app1.tenant1.us-central-1.vespa.oath.cloud",
                "app1.tenant1.eu-west-1.vespa.oath.cloud",
                "*.app1.tenant1.eu-west-1.vespa.oath.cloud"
        ), tester.controllerTester().serviceRegistry().applicationCertificateMock().dnsNamesOf(context1.instanceId()));

        // Next deployment reuses certificate
        context1.submit(applicationPackage).deploy();
        assertEquals(cert, certificate.apply(context1.instance()));

        // Create app2
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");
        ZoneId zone = ZoneId.from("dev", "us-east-1");

        // Deploy app2 in dev
        tester.controller().applications().deploy(context2.instanceId(), zone, Optional.of(applicationPackage), DeployOptions.none());
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context2.instanceId(), zone).get().activated());
        assertFalse("Does not provision certificate in " + Environment.dev, certificate.apply(context2.instance()).isPresent());
    }

    @Test
    public void testDeployWithCrossCloudEndpoints() {
        tester.controllerTester().zoneRegistry().setZones(
                ZoneApiMock.fromId("prod.us-west-1"),
                ZoneApiMock.newBuilder().with(CloudName.from("aws")).withId("prod.aws-us-east-1").build()
        );
        var context = tester.deploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .region("aws-us-east-1")
                .region("us-west-1")
                .endpoint("default", "default") // Contains to all regions by default
                .build();

        try {
            context.submit(applicationPackage);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Endpoint 'default' in instance 'default' cannot contain regions in different clouds: [aws-us-east-1, us-west-1]", e.getMessage());
        }

        var applicationPackage2 = new ApplicationPackageBuilder()
                .region("aws-us-east-1")
                .region("us-west-1")
                .endpoint("aws", "default", "aws-us-east-1")
                .endpoint("foo", "default", "aws-us-east-1", "us-west-1")
                .build();
        try {
            context.submit(applicationPackage2);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Endpoint 'foo' in instance 'default' cannot contain regions in different clouds: [aws-us-east-1, us-west-1]", e.getMessage());
        }
    }

    private void assertStatus(JobStatus expectedStatus, ApplicationId id, Controller controller) {
        Instance app = controller.applications().getInstance(id).get();
        JobStatus existingStatus = app.deploymentJobs().jobStatus().get(expectedStatus.type());
        assertNotNull("Status of type " + expectedStatus.type() + " is present", existingStatus);
        assertEquals(expectedStatus, existingStatus);
    }

}
