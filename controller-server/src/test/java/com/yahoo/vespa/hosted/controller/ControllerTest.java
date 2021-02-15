// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.Sets;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedAliasTarget;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertArrayEquals;
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

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void testDeployment() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // staging job - succeeding
        Version version1 = tester.configServer().initialVersion();
        var context = tester.newDeploymentContext();
        context.submit(applicationPackage);
        assertEquals("Application version is known from completion of initial job",
                     ApplicationVersion.from(DeploymentContext.defaultSourceRevision, 1, "a@b", new Version("6.1"), Instant.ofEpochSecond(1)),
                     context.instance().change().application().get());
        context.runJob(systemTest);
        context.runJob(stagingTest);

        ApplicationVersion applicationVersion = context.instance().change().application().get();
        assertFalse("Application version has been set during deployment", applicationVersion.isUnknown());

        tester.triggerJobs();
        // Causes first deployment job to be triggered
        tester.clock().advance(Duration.ofSeconds(1));

        // production job (failing) after deployment
        context.timeOutUpgrade(productionUsWest1);
        assertEquals(4, context.instanceJobs().size());
        tester.triggerJobs();

        // Simulate restart
        tester.controllerTester().createNewController();

        assertNotNull(tester.controller().tenants().get(TenantName.from("tenant1")));
        assertNotNull(tester.controller().applications().requireInstance(context.instanceId()));

        // system and staging test job - succeeding
        context.submit(applicationPackage);
        context.runJob(systemTest);
        context.runJob(stagingTest);

        // production job succeeding now
        context.jobAborted(productionUsWest1);
        context.runJob(productionUsWest1);

        // causes triggering of next production job
        tester.triggerJobs();
        context.runJob(productionUsEast3);

        assertEquals(4, context.instanceJobs().size());

        // Instance with uppercase characters is not allowed.
        applicationPackage = new ApplicationPackageBuilder()
                .instances("hellO")
                .build();
        try {
            context.submit(applicationPackage);
            fail("Expected exception due to illegal deployment spec.");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Invalid id 'hellO'. Tenant, application and instance names must start with a letter, may contain no more than 20 characters, and may only contain lowercase letters, digits or dashes, but no double-dashes.", e.getMessage());
        }

        // Production zone for which there is no JobType is not allowed.
        applicationPackage = new ApplicationPackageBuilder()
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

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .region("us-east-3")
                .build();
        context.submit(applicationPackage);
        assertNull("Zone was removed",
                   context.instance().deployments().get(productionUsWest1.zone(main)));
        assertNull("Deployment job was removed", context.instanceJobs().get(productionUsWest1));

        // Submission has stored application meta.
        assertNotNull(tester.controllerTester().serviceRegistry().applicationStore()
                            .getMeta(context.instanceId())
                            .get(tester.clock().instant()));

        // Meta data tombstone placed on delete
        tester.clock().advance(Duration.ofSeconds(1));
        context.submit(ApplicationPackage.deploymentRemoval());
        tester.clock().advance(Duration.ofSeconds(1));
        context.submit(ApplicationPackage.deploymentRemoval());
        tester.applications().deleteApplication(context.application().id(),
                                                tester.controllerTester().credentialsFor(context.instanceId().tenant()));
        assertArrayEquals(new byte[0],
                          tester.controllerTester().serviceRegistry().applicationStore()
                                .getMeta(context.instanceId())
                                .get(tester.clock().instant()));

        assertNull(tester.controllerTester().serviceRegistry().applicationStore()
                         .getMeta(context.deploymentIdIn(productionUsWest1.zone(main))));
    }

    @Test
    public void testGlobalRotations() {
        var context = tester.newDeploymentContext();
        var zone1 = ZoneId.from("prod", "us-west-1");
        var zone2 = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("default", "default", zone1.region().value(), zone2.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        // Check initial rotation status
        var deployment1 = context.deploymentIdIn(zone1);
        var status1 = tester.controller().routing().globalRotationStatus(deployment1);
        assertEquals(1, status1.size());
        assertTrue("All upstreams are in", status1.values().stream().allMatch(es -> es.getStatus() == EndpointStatus.Status.in));

        // Set the deployment out of service in the global rotation
        var newStatus = new EndpointStatus(EndpointStatus.Status.out, "unit-test", ControllerTest.class.getSimpleName(), tester.clock().instant().getEpochSecond());
        tester.controller().routing().setGlobalRotationStatus(deployment1, newStatus);
        status1 = tester.controller().routing().globalRotationStatus(deployment1);
        assertEquals(1, status1.size());
        assertTrue("All upstreams are out", status1.values().stream().allMatch(es -> es.getStatus() == EndpointStatus.Status.out));
        assertTrue("Reason is set", status1.values().stream().allMatch(es -> es.getReason().equals("unit-test")));

        // Other deployment remains in
        var status2 = tester.controller().routing().globalRotationStatus(context.deploymentIdIn(zone2));
        assertEquals(1, status2.size());
        assertTrue("All upstreams are in", status2.values().stream().allMatch(es -> es.getStatus() == EndpointStatus.Status.in));
    }

    @Test
    public void testDnsAliasRegistration() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
                .endpoint("foobar", "qrs", "us-west-1", "us-central-1")  // Rotation 01
                .endpoint("default", "qrs", "us-west-1", "us-central-1") // Rotation 02
                .endpoint("all", "qrs")                                  // Rotation 03
                .endpoint("west", "qrs", "us-west-1")                    // Rotation 04
                .region("us-west-1")
                .region("us-central-1")
                .build();
        context.submit(applicationPackage).deploy();

        Collection<Deployment> deployments = context.instance().deployments().values();
        assertFalse(deployments.isEmpty());

        var notWest = Set.of(
                "rotation-id-01", "foobar--app1--tenant1.global.vespa.oath.cloud",
                "rotation-id-02", "app1--tenant1.global.vespa.oath.cloud",
                "rotation-id-03", "all--app1--tenant1.global.vespa.oath.cloud"
        );
        var west = Sets.union(notWest, Set.of("rotation-id-04", "west--app1--tenant1.global.vespa.oath.cloud"));

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
        assertEquals("rotation-fqdn-02.", record1.get().data().asString());

        var record2 = tester.controllerTester().findCname("foobar--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record2.isPresent());
        assertEquals("foobar--app1--tenant1.global.vespa.oath.cloud", record2.get().name().asString());
        assertEquals("rotation-fqdn-01.", record2.get().data().asString());

        var record3 = tester.controllerTester().findCname("all--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record3.isPresent());
        assertEquals("all--app1--tenant1.global.vespa.oath.cloud", record3.get().name().asString());
        assertEquals("rotation-fqdn-03.", record3.get().data().asString());

        var record4 = tester.controllerTester().findCname("west--app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record4.isPresent());
        assertEquals("west--app1--tenant1.global.vespa.oath.cloud", record4.get().name().asString());
        assertEquals("rotation-fqdn-04.", record4.get().data().asString());
    }

    @Test
    public void testDnsAliasRegistrationWithChangingEndpoints() {
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        var west = ZoneId.from("prod", "us-west-1");
        var central = ZoneId.from("prod", "us-central-1");
        var east = ZoneId.from("prod", "us-east-3");

        // Application is deployed with endpoint pointing to 2/3 zones
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
        var context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .endpoint("default", "qrs", "us-west-1", "us-central-1")
                .region("us-west-1")
                .region("us-central-1")
                .build();
        context.submit(applicationPackage).deploy();

        ApplicationPackage applicationPackage2 = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-central-1")
                .allow(ValidationId.globalEndpointChange)
                .build();

        context.submit(applicationPackage2).deploy();

        assertEquals(List.of(), context.instance().rotations());

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
                    .allow(ValidationId.deploymentRemoval)
                    .allow(ValidationId.globalEndpointChange)
                    .build();
            context.submit(applicationPackage);
            tester.applications().deleteApplication(context.application().id(),
                                                    tester.controllerTester().credentialsFor(context.application().id().tenant()));
            try (RotationLock lock = tester.controller().routing().rotations().lock()) {
                assertTrue("Rotation is unassigned",
                           tester.controller().routing().rotations().availableRotations(lock)
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
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneApiMock.fromId("prod.cd-us-central-1"));
        tester.configServer().bootstrap(List.of(ZoneId.from("prod.cd-us-central-1")), SystemApplication.all());
        tester.controllerTester().upgradeSystem(six);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .majorVersion(6)
                .region("cd-us-central-1")
                .build();

        // Create application
        var context = tester.newDeploymentContext();

        // Direct deploy is allowed when deployDirectly is true
        ZoneId zone = ZoneId.from("prod", "cd-us-central-1");
        // Same options as used in our integration tests
        DeployOptions options = new DeployOptions(true, Optional.empty(), false,
                                                  false);
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), options);

        assertTrue("Application deployed and activated",
                   tester.configServer().application(context.instanceId(), zone).get().activated());

        assertTrue("No job status added",
                   context.instanceJobs().isEmpty());

        Version seven = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(seven);
        tester.upgrader().maintain();
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), options);
        assertEquals(six, context.instance().deployments().get(zone).version());
    }

    @Test
    public void testDevDeployment() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().build();

        // Create application
        var context = tester.newDeploymentContext();
        ZoneId zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().zoneRegistry()
                .setRoutingMethod(ZoneApiMock.from(zone), RoutingMethod.shared, RoutingMethod.sharedLayer4);

        // Deploy
        tester.controller().applications().deploy(context.instanceId(), zone, Optional.of(applicationPackage), DeployOptions.none());
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context.instanceId(), zone).get().activated());
        assertTrue("No job status added",
                   context.instanceJobs().isEmpty());
        assertEquals("DeploymentSpec is not stored", DeploymentSpec.empty, context.application().deploymentSpec());

        // Verify zone supports shared layer 4 and shared routing methods
        Set<RoutingMethod> routingMethods = tester.controller().routing().endpointsOf(context.deploymentIdIn(zone))
                .asList()
                .stream()
                .map(Endpoint::routingMethod)
                .collect(Collectors.toSet());
        assertEquals(routingMethods, Set.of(RoutingMethod.shared, RoutingMethod.sharedLayer4));

        // Deployment has stored application meta.
        assertNotNull(tester.controllerTester().serviceRegistry().applicationStore()
                            .getMeta(new DeploymentId(context.instanceId(), zone))
                            .get(tester.clock().instant()));

        // Meta data tombstone placed on delete
        tester.clock().advance(Duration.ofSeconds(1));
        tester.controller().applications().deactivate(context.instanceId(), zone);
        assertArrayEquals(new byte[0],
                          tester.controllerTester().serviceRegistry().applicationStore()
                                .getMeta(new DeploymentId(context.instanceId(), zone))
                                .get(tester.clock().instant()));
    }

    @Test
    public void testSuspension() {
        var context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .region("us-west-1")
                                                        .region("us-east-3")
                                                        .build();
        context.submit(applicationPackage).deploy();

        DeploymentId deployment1 = context.deploymentIdIn(ZoneId.from(Environment.prod, RegionName.from("us-west-1")));
        DeploymentId deployment2 = context.deploymentIdIn(ZoneId.from(Environment.prod, RegionName.from("us-east-3")));
        assertFalse(tester.configServer().isSuspended(deployment1));
        assertFalse(tester.configServer().isSuspended(deployment2));
        tester.configServer().setSuspension(deployment1, true);
        assertTrue(tester.configServer().isSuspended(deployment1));
        assertFalse(tester.configServer().isSuspended(deployment2));
    }

    // Application may already have been deleted, or deployment failed without response, test that deleting a
    // second time will not fail
    @Test
    public void testDeletingApplicationThatHasAlreadyBeenDeleted() {
        var context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
                .region("us-west-1")
                .build(true);
        tester.newDeploymentContext().submit(applicationPackage);
    }

    @Test
    public void testDeployApplicationWithWarnings() {
        var context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
        Function<Instance, Optional<EndpointCertificateMetadata>> certificate = (application) -> tester.controller().curator().readEndpointCertificateMetadata(application.id());

        // Create app1
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var prodZone = ZoneId.from("prod", "us-west-1");
        var stagingZone = ZoneId.from("staging", "us-east-3");
        var testZone = ZoneId.from("test", "us-east-1");
        tester.controllerTester().zoneRegistry().exclusiveRoutingIn(ZoneApiMock.from(prodZone));
        var applicationPackage = new ApplicationPackageBuilder().athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                                                                .compileVersion(RoutingController.DIRECT_ROUTING_MIN_VERSION)
                                                                .region(prodZone.region())
                                                                .build();
        // Deploy app1 in production
        context1.submit(applicationPackage).deploy();
        var cert = certificate.apply(context1.instance());
        assertTrue("Provisions certificate in " + Environment.prod, cert.isPresent());
        assertEquals(Stream.concat(Stream.of("vznqtz7a5ygwjkbhhj7ymxvlrekgt4l6g.vespa.oath.cloud",
                                             "app1.tenant1.global.vespa.oath.cloud",
                                             "*.app1.tenant1.global.vespa.oath.cloud"),
                                   Stream.of(prodZone, testZone, stagingZone)
                                         .flatMap(zone -> Stream.of("", "*.")
                                                                .map(prefix -> prefix + "app1.tenant1." + zone.region().value() +
                                                                               (zone.environment() == Environment.prod ? "" :  "." + zone.environment().value()) +
                                                                               ".vespa.oath.cloud")))
                           .collect(Collectors.toUnmodifiableSet()),
                     Set.copyOf(tester.controllerTester().serviceRegistry().endpointCertificateMock().dnsNamesOf(context1.instanceId())));

        // Next deployment reuses certificate
        context1.submit(applicationPackage).deploy();
        assertEquals(cert, certificate.apply(context1.instance()));

        // Create app2
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");
        var devZone = ZoneId.from("dev", "us-east-1");

        // Deploy app2 in a zone with shared routing
        tester.controller().applications().deploy(context2.instanceId(), devZone, Optional.of(applicationPackage), DeployOptions.none());
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context2.instanceId(), devZone).get().activated());
        assertTrue("Provisions certificate also in zone with routing layer", certificate.apply(context2.instance()).isPresent());
    }

    @Test
    public void testDeployWithCrossCloudEndpoints() {
        tester.controllerTester().zoneRegistry().setZones(
                ZoneApiMock.fromId("prod.us-west-1"),
                ZoneApiMock.newBuilder().with(CloudName.from("aws")).withId("prod.aws-us-east-1").build()
        );
        var context = tester.newDeploymentContext();
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

    @Test
    public void testDeployWithoutSourceRevision() {
        var context = tester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .region("us-west-1")
                .build();

        // Submit without source revision
        context.submit(applicationPackage, Optional.empty())
               .deploy();
        assertEquals("Deployed application", 1, context.instance().deployments().size());
    }

    @Test
    public void testDeployWithGlobalEndpointsAndMultipleRoutingMethods() {
        var context = tester.newDeploymentContext();
        var zone1 = ZoneId.from("prod", "us-west-1");
        var zone2 = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .compileVersion(RoutingController.DIRECT_ROUTING_MIN_VERSION)
                .endpoint("default", "default", zone1.region().value(), zone2.region().value())
                .endpoint("east", "default", zone2.region().value())
                .region(zone1.region())
                .region(zone2.region())
                .build();

        // Zone 1 supports shared and sharedLayer4
        tester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(zone1), RoutingMethod.shared,
                                                                  RoutingMethod.sharedLayer4);
        // Zone 2 supports shared and exclusive
        tester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(zone2), RoutingMethod.shared,
                                                                  RoutingMethod.exclusive);

        context.submit(applicationPackage).deploy();
        var expectedRecords = List.of(
                // The weighted record for zone 2's region
                new Record(Record.Type.ALIAS,
                           RecordName.from("application.tenant.us-east-3-w.vespa.oath.cloud"),
                           new WeightedAliasTarget(HostName.from("lb-0--tenant:application:default--prod.us-east-3"),
                                                   "dns-zone-1", ZoneId.from("prod.us-east-3"), 1).pack()),

                // The 'east' global endpoint, pointing to the weighted record for zone 2's region
                new Record(Record.Type.ALIAS,
                           RecordName.from("east.application.tenant.global.vespa.oath.cloud"),
                           new LatencyAliasTarget(HostName.from("application.tenant.us-east-3-w.vespa.oath.cloud"),
                                                  "dns-zone-1", ZoneId.from("prod.us-east-3")).pack()),

                // The 'default' global endpoint, pointing to both zones with shared routing, via rotation
                new Record(Record.Type.CNAME,
                           RecordName.from("application--tenant.global.vespa.oath.cloud"),
                           RecordData.from("rotation-fqdn-01.")),

                // The zone-scoped endpoint pointing to zone 2 with exclusive routing
                new Record(Record.Type.CNAME,
                           RecordName.from("application.tenant.us-east-3.vespa.oath.cloud"),
                           RecordData.from("lb-0--tenant:application:default--prod.us-east-3.")),

                // The 'east' global endpoint, pointing to zone 2 with shared routing, via rotation
                new Record(Record.Type.CNAME,
                           RecordName.from("east--application--tenant.global.vespa.oath.cloud"),
                           RecordData.from("rotation-fqdn-02.")));
        assertEquals(expectedRecords, List.copyOf(tester.controllerTester().nameService().records()));
    }

    @Test
    public void testDirectRoutingSupport() {
        var context = tester.newDeploymentContext();
        var zone1 = ZoneId.from("prod", "us-west-1");
        var zone2 = ZoneId.from("prod", "us-east-3");
        var applicationPackageBuilder = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region());
        tester.controllerTester().zoneRegistry()
              .setRoutingMethod(ZoneApiMock.from(zone1), RoutingMethod.shared, RoutingMethod.sharedLayer4)
              .setRoutingMethod(ZoneApiMock.from(zone2), RoutingMethod.shared, RoutingMethod.sharedLayer4);
        Supplier<Set<RoutingMethod>> routingMethods = () -> tester.controller().routing().endpointsOf(context.deploymentIdIn(zone1))
                                                                  .asList()
                                                                  .stream()
                                                                  .map(Endpoint::routingMethod)
                                                                  .collect(Collectors.toSet());

        // Without satisfying any requirement
        context.submit(applicationPackageBuilder.build()).deploy();
        assertEquals(Set.of(RoutingMethod.shared), routingMethods.get());

        // Without satisfying version requirement
        applicationPackageBuilder = applicationPackageBuilder.athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"));
        context.submit(applicationPackageBuilder.build()).deploy();
        assertEquals(Set.of(RoutingMethod.shared), routingMethods.get());

        // Package satisfying all requirements is submitted, but not deployed yet
        applicationPackageBuilder = applicationPackageBuilder.compileVersion(RoutingController.DIRECT_ROUTING_MIN_VERSION);
        var context2 = context.submit(applicationPackageBuilder.build());
        assertEquals("Direct routing endpoint is available after submission and before deploy",
                     Set.of(RoutingMethod.shared, RoutingMethod.sharedLayer4), routingMethods.get());
        context2.deploy();

        // Global endpoint is added and includes directly routed endpoint name
        applicationPackageBuilder = applicationPackageBuilder.endpoint("default", "default");
        context2.submit(applicationPackageBuilder.build()).deploy();
        for (var zone : List.of(zone1, zone2)) {
            assertEquals(Set.of("rotation-id-01",
                                "application.tenant.global.vespa.oath.cloud",
                                "application--tenant.global.vespa.oath.cloud"),
                         tester.configServer().rotationNames().get(context.deploymentIdIn(zone)));
        }
    }

    @Test
    public void testChangeEndpointCluster() {
        var context = tester.newDeploymentContext();
        var west = ZoneId.from("prod", "us-west-1");
        var east = ZoneId.from("prod", "us-east-3");

        // Deploy application
        var applicationPackage = new ApplicationPackageBuilder()
                .endpoint("default", "foo")
                .region(west.region().value())
                .region(east.region().value())
                .build();
        context.submit(applicationPackage).deploy();
        assertEquals(ClusterSpec.Id.from("foo"), tester.applications().requireInstance(context.instanceId())
                                                       .rotations().get(0).clusterId());

        // Redeploy with endpoint cluster changed needs override
        applicationPackage = new ApplicationPackageBuilder()
                .endpoint("default", "bar")
                .region(west.region().value())
                .region(east.region().value())
                .build();
        try {
            context.submit(applicationPackage).deploy();
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("global-endpoint-change: application 'tenant.application' has endpoints [endpoint " +
                         "'default' (cluster foo) -> us-east-3, us-west-1], but does not include all of these in " +
                         "deployment.xml. Deploying given deployment.xml will remove " +
                         "[endpoint 'default' (cluster foo) -> us-east-3, us-west-1] and add " +
                         "[endpoint 'default' (cluster bar) -> us-east-3, us-west-1]. To allow this add " +
                         "<allow until='yyyy-mm-dd'>global-endpoint-change</allow> to validation-overrides.xml, see " +
                         "https://docs.vespa.ai/en/reference/validation-overrides.html", e.getMessage());
        }

        // Redeploy with override succeeds
        applicationPackage = new ApplicationPackageBuilder()
                .endpoint("default", "bar")
                .region(west.region().value())
                .region(east.region().value())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context.submit(applicationPackage).deploy();
        assertEquals(ClusterSpec.Id.from("bar"), tester.applications().requireInstance(context.instanceId())
                                                       .rotations().get(0).clusterId());
    }

    @Test
    public void testReadableApplications() {
        var db = new MockCuratorDb();
        var tester = new DeploymentTester(new ControllerTester(db));

        // Create and deploy two applications
        var app1 = tester.newDeploymentContext("t1", "a1", "default")
                         .submit()
                         .deploy();
        var app2 = tester.newDeploymentContext("t2", "a2", "default")
                         .submit()
                         .deploy();
        assertEquals(2, tester.applications().readable().size());

        // Write invalid data to one application
        db.curator().set(Path.fromString("/controller/v1/applications/" + app2.application().id().serialized()),
                         new byte[]{(byte) 0xDE, (byte) 0xAD});

        // Can read the remaining readable
        assertEquals(1, tester.applications().readable().size());

        // Unconditionally reading all applications fails
        try {
            tester.applications().asList();
            fail("Expected exception");
        } catch (Exception ignored) {
        }

        // Deployment for readable application still succeeds
        app1.submit().deploy();
    }

    @Test
    public void testClashingEndpointIdAndInstanceName() {
        String deploymentXml = "<deployment version='1.0' athenz-domain='domain' athenz-service='service'>\n" +
                               "  <instance id=\"default\">\n" +
                               "    <prod>\n" +
                               "      <region active=\"true\">us-west-1</region>\n" +
                               "    </prod>\n" +
                               "    <endpoints>\n" +
                               "      <endpoint id=\"dev\" container-id=\"qrs\"/>\n" +
                               "    </endpoints>\n" +
                               "  </instance>\n" +
                               "  <instance id=\"dev\">\n" +
                               "    <prod>\n" +
                               "      <region active=\"true\">us-west-1</region>\n" +
                               "    </prod>\n" +
                               "    <endpoints>\n" +
                               "      <endpoint id=\"default\" container-id=\"qrs\"/>\n" +
                               "    </endpoints>\n" +
                               "  </instance>\n" +
                               "</deployment>\n";
        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(deploymentXml);
        try {
            tester.newDeploymentContext().submit(applicationPackage);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Endpoint with ID 'default' in instance 'dev' clashes with endpoint 'dev' in instance 'default'",
                         e.getMessage());
        }
    }

}
