// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.Sets;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedAliasTarget;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Submission;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.Notification.Level;
import com.yahoo.vespa.hosted.controller.notification.Notification.Type;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationLock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
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
                     ApplicationVersion.from(RevisionId.forProduction(1), DeploymentContext.defaultSourceRevision, "a@b", new Version("6.1"), Instant.ofEpochSecond(1)),
                     context.application().revisions().get(context.instance().change().revision().get()));
        context.runJob(systemTest);
        context.runJob(stagingTest);

        RevisionId applicationVersion = context.instance().change().revision().get();
        assertTrue("Application version has been set during deployment", applicationVersion.isProduction());

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
        context.triggerJobs().jobAborted(productionUsWest1);
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
                      context.instance().deployments().get(productionUsWest1.zone()));

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .region("us-east-3")
                .build();
        context.submit(applicationPackage);
        assertNull("Zone was removed",
                   context.instance().deployments().get(productionUsWest1.zone()));
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
                         .getMeta(context.deploymentIdIn(productionUsWest1.zone())));
    }

    @Test
    public void testGlobalRotationStatus() {
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
        DeploymentRoutingContext routingContext = tester.controller().routing().of(deployment1);
        RoutingStatus status1 = routingContext.routingStatus();
        assertEquals(RoutingStatus.Value.in, status1.value());

        // Set the deployment out of service in the global rotation
        routingContext.setRoutingStatus(RoutingStatus.Value.out, RoutingStatus.Agent.operator);
        RoutingStatus status2 = routingContext.routingStatus();
        assertEquals(RoutingStatus.Value.out, status2.value());

        // Other deployment remains in
        RoutingStatus status3 = tester.controller().routing().of(context.deploymentIdIn(zone2)).routingStatus();
        assertEquals(RoutingStatus.Value.in, status3.value());
    }

    @Test
    public void testDnsUpdatesForGlobalEndpoint() {
        var betaContext = tester.newDeploymentContext("tenant1", "app1", "beta");
        var defaultContext = tester.newDeploymentContext("tenant1", "app1", "default");

        ZoneId usWest = ZoneId.from("prod.us-west-1");
        ZoneId usCentral = ZoneId.from("prod.us-central-1");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("beta,default")
                .endpoint("default", "foo")
                .region(usWest.region())
                .region(usCentral.region()) // Two deployments should result in each DNS alias being registered once
                .build();
        tester.controllerTester().zoneRegistry().setRoutingMethod(List.of(ZoneApiMock.from(usWest), ZoneApiMock.from(usCentral)),
                                                                  RoutingMethod.sharedLayer4);
        betaContext.submit(applicationPackage).deploy();

        { // Expected rotation names are passed to beta instance deployments
            Collection<Deployment> betaDeployments = betaContext.instance().deployments().values();
            assertFalse(betaDeployments.isEmpty());
            Set<ContainerEndpoint> containerEndpoints = Set.of(new ContainerEndpoint("foo",
                                                                                     "global",
                                                                                     List.of("beta.app1.tenant1.global.vespa.oath.cloud",
                                                                                             "rotation-id-01"),
                                                                                     OptionalInt.empty(),
                                                                                     RoutingMethod.sharedLayer4));

            for (Deployment deployment : betaDeployments) {
                assertEquals(containerEndpoints,
                             tester.configServer().containerEndpoints()
                                   .get(betaContext.deploymentIdIn(deployment.zone())));
            }
            betaContext.flushDnsUpdates();
        }

        { // Expected rotation names are passed to default instance deployments
            Collection<Deployment> defaultDeployments = defaultContext.instance().deployments().values();
            assertFalse(defaultDeployments.isEmpty());
            Set<ContainerEndpoint> containerEndpoints = Set.of(new ContainerEndpoint("foo",
                                                                                     "global",
                                                                                     List.of("app1.tenant1.global.vespa.oath.cloud",
                                                                                             "rotation-id-02"),
                                                                                     OptionalInt.empty(),
                                                                                     RoutingMethod.sharedLayer4));
            for (Deployment deployment : defaultDeployments) {
                assertEquals(containerEndpoints,
                             tester.configServer().containerEndpoints().get(defaultContext.deploymentIdIn(deployment.zone())));
            }
            defaultContext.flushDnsUpdates();
        }

        Map<String, String> rotationCnames = Map.of("beta.app1.tenant1.global.vespa.oath.cloud", "rotation-fqdn-01.",
                                                    "app1.tenant1.global.vespa.oath.cloud", "rotation-fqdn-02.");
        rotationCnames.forEach((cname, data) -> {
            var record = tester.controllerTester().findCname(cname);
            assertTrue(record.isPresent());
            assertEquals(cname, record.get().name().asString());
            assertEquals(data, record.get().data().asString());
        });

        Map<ApplicationId, Set<String>> globalDnsNamesByInstance = Map.of(betaContext.instanceId(), Set.of("beta.app1.tenant1.global.vespa.oath.cloud"),
                                                                          defaultContext.instanceId(), Set.of("app1.tenant1.global.vespa.oath.cloud"));

        globalDnsNamesByInstance.forEach((instance, dnsNames) -> {
            Set<String> actualDnsNames = tester.controller().routing().readDeclaredEndpointsOf(instance)
                                               .scope(Endpoint.Scope.global)
                                               .asList().stream()
                                               .map(Endpoint::dnsName)
                                               .collect(Collectors.toSet());
            assertEquals("Global DNS names for " + instance, dnsNames, actualDnsNames);
        });
    }

    @Test
    public void testDnsUpdatesForGlobalEndpointLegacySyntax() {
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
                            "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(deployment.zone())));
        }
        context.flushDnsUpdates();
        assertEquals(1, tester.controllerTester().nameService().records().size());

        Optional<Record> record = tester.controllerTester().findCname("app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        List<String> globalDnsNames = tester.controller().routing().readDeclaredEndpointsOf(context.instanceId())
                                            .scope(Endpoint.Scope.global)
                                            .sortedBy(Comparator.comparing(Endpoint::dnsName))
                                            .mapToList(Endpoint::dnsName);
        assertEquals(List.of("app1.tenant1.global.vespa.oath.cloud"),
                     globalDnsNames);
    }

    @Test
    public void testDnsUpdatesForMultipleGlobalEndpoints() {
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
                "rotation-id-01", "foobar.app1.tenant1.global.vespa.oath.cloud",
                "rotation-id-02", "app1.tenant1.global.vespa.oath.cloud",
                "rotation-id-03", "all.app1.tenant1.global.vespa.oath.cloud"
        );
        var west = Sets.union(notWest, Set.of("rotation-id-04", "west.app1.tenant1.global.vespa.oath.cloud"));

        for (Deployment deployment : deployments) {
            assertEquals("Rotation names are passed to config server in " + deployment.zone(),
                    ZoneId.from("prod.us-west-1").equals(deployment.zone()) ? west : notWest,
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(deployment.zone())));
        }
        context.flushDnsUpdates();

        assertEquals(4, tester.controllerTester().nameService().records().size());

        var record1 = tester.controllerTester().findCname("app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record1.isPresent());
        assertEquals("app1.tenant1.global.vespa.oath.cloud", record1.get().name().asString());
        assertEquals("rotation-fqdn-02.", record1.get().data().asString());

        var record2 = tester.controllerTester().findCname("foobar.app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record2.isPresent());
        assertEquals("foobar.app1.tenant1.global.vespa.oath.cloud", record2.get().name().asString());
        assertEquals("rotation-fqdn-01.", record2.get().data().asString());

        var record3 = tester.controllerTester().findCname("all.app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record3.isPresent());
        assertEquals("all.app1.tenant1.global.vespa.oath.cloud", record3.get().name().asString());
        assertEquals("rotation-fqdn-03.", record3.get().data().asString());

        var record4 = tester.controllerTester().findCname("west.app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record4.isPresent());
        assertEquals("west.app1.tenant1.global.vespa.oath.cloud", record4.get().name().asString());
        assertEquals("rotation-fqdn-04.", record4.get().data().asString());
    }

    @Test
    public void testDnsUpdatesForGlobalEndpointChanges() {
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
                    Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
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
                    Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
            );
        }
        assertEquals(
                "Zone " + east + " is a member of global endpoint",
                Set.of("rotation-id-02", "east.app1.tenant1.global.vespa.oath.cloud"),
                tester.configServer().containerEndpointNames(context.deploymentIdIn(east))
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
                            ? Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud",
                                     "rotation-id-02", "east.app1.tenant1.global.vespa.oath.cloud")
                            : Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
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
                tester.configServer().containerEndpoints().get(context.deploymentIdIn(ZoneId.from("prod", "us-west-1")))
        );
    }

    @Test
    public void testDnsUpdatesWithChangeInRotationAssignment() {
        // Application 1 is deployed and deleted
        String dnsName1 = "app1.tenant1.global.vespa.oath.cloud";
        {
            var context = tester.newDeploymentContext("tenant1", "app1", "default");
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .endpoint("default", "foo")
                    .region("us-west-1")
                    .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                    .build();

            context.submit(applicationPackage).deploy();
            assertEquals(1, tester.controllerTester().nameService().records().size());
            {
                Optional<Record> record = tester.controllerTester().findCname(dnsName1);
                assertTrue(record.isPresent());
                assertEquals(dnsName1, record.get().name().asString());
                assertEquals("rotation-fqdn-01.", record.get().data().asString());
            }

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

            // Record is removed
            Optional<Record> record = tester.controllerTester().findCname(dnsName1);
            assertTrue(dnsName1 + " is removed", record.isEmpty());
        }

        // Application 2 is deployed and assigned same rotation as application 1 had before deletion
        String dnsName2 = "app2.tenant2.global.vespa.oath.cloud";
        {
            var context = tester.newDeploymentContext("tenant2", "app2", "default");
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .endpoint("default", "foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            context.submit(applicationPackage).deploy();
            assertEquals(1, tester.controllerTester().nameService().records().size());

            var record = tester.controllerTester().findCname(dnsName2);
            assertTrue(record.isPresent());
            assertEquals(dnsName2, record.get().name().asString());
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

            var record1 = tester.controllerTester().findCname(dnsName1);
            assertTrue(record1.isPresent());
            assertEquals("rotation-fqdn-02.", record1.get().data().asString());

            var record2 = tester.controllerTester().findCname(dnsName2);
            assertTrue(record2.isPresent());
            assertEquals("rotation-fqdn-01.", record2.get().data().asString());
        }

    }

    @Test
    public void testDnsUpdatesForApplicationEndpoint() {
        ApplicationId beta = ApplicationId.from("tenant1", "app1", "beta");
        ApplicationId main = ApplicationId.from("tenant1", "app1", "main");
        var context = tester.newDeploymentContext(beta);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("beta,main")
                .region("us-west-1")
                .region("us-east-3")
                .applicationEndpoint("a", "default", "us-west-1",
                                     Map.of(beta.instance(), 2,
                                            main.instance(), 8))
                .applicationEndpoint("b", "default", "us-west-1",
                                     Map.of(beta.instance(), 1,
                                            main.instance(), 1))
                .applicationEndpoint("c", "default", "us-east-3",
                                     Map.of(beta.instance(), 4,
                                            main.instance(), 6))
                .build();
        context.submit(applicationPackage).deploy();

        ZoneId usWest = ZoneId.from("prod", "us-west-1");
        ZoneId usEast = ZoneId.from("prod", "us-east-3");
        // Expected container endpoints are passed to each deployment
        Map<DeploymentId, Map<String, Integer>> deploymentEndpoints = Map.of(
                new DeploymentId(beta, usWest), Map.of("a.app1.tenant1.us-west-1-r.vespa.oath.cloud", 2,
                                                       "b.app1.tenant1.us-west-1-r.vespa.oath.cloud", 1),
                new DeploymentId(main, usWest), Map.of("a.app1.tenant1.us-west-1-r.vespa.oath.cloud", 8,
                                                       "b.app1.tenant1.us-west-1-r.vespa.oath.cloud", 1),
                new DeploymentId(beta, usEast), Map.of("c.app1.tenant1.us-east-3-r.vespa.oath.cloud", 4),
                new DeploymentId(main, usEast), Map.of("c.app1.tenant1.us-east-3-r.vespa.oath.cloud", 6)
        );
        deploymentEndpoints.forEach((deployment, endpoints) -> {
            Set<ContainerEndpoint> expected = endpoints.entrySet().stream()
                                                       .map(kv -> new ContainerEndpoint("default", "application",
                                                                                        List.of(kv.getKey()),
                                                                                        OptionalInt.of(kv.getValue()),
                                                                                        RoutingMethod.sharedLayer4))
                                                       .collect(Collectors.toSet());
            assertEquals("Endpoint names for " + deployment + " are passed to config server",
                         expected,
                         tester.configServer().containerEndpoints().get(deployment));
        });
        context.flushDnsUpdates();

        // DNS records are created for each endpoint
        Set<Record> records = tester.controllerTester().nameService().records();
        assertEquals(Set.of(new Record(Record.Type.CNAME,
                                       RecordName.from("a.app1.tenant1.us-west-1-r.vespa.oath.cloud"),
                                       RecordData.from("vip.prod.us-west-1.")),
                            new Record(Record.Type.CNAME,
                                       RecordName.from("b.app1.tenant1.us-west-1-r.vespa.oath.cloud"),
                                       RecordData.from("vip.prod.us-west-1.")),
                            new Record(Record.Type.CNAME,
                                       RecordName.from("c.app1.tenant1.us-east-3-r.vespa.oath.cloud"),
                                       RecordData.from("vip.prod.us-east-3."))),
                     records);
        List<String> endpointDnsNames = tester.controller().routing().declaredEndpointsOf(context.application())
                                              .scope(Endpoint.Scope.application)
                                              .mapToList(Endpoint::dnsName);
        assertEquals(List.of("a.app1.tenant1.us-west-1-r.vespa.oath.cloud",
                             "b.app1.tenant1.us-west-1-r.vespa.oath.cloud",
                             "c.app1.tenant1.us-east-3-r.vespa.oath.cloud"),
                     endpointDnsNames);
    }

    @Test
    public void testDevDeployment() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().build();

        // Create application
        var context = tester.newDeploymentContext();
        ZoneId zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().zoneRegistry()
              .setRoutingMethod(ZoneApiMock.from(zone), RoutingMethod.sharedLayer4);

        // Deploy
        context.runJob(zone, applicationPackage);
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context.instanceId(), zone).get().activated());
        assertTrue("No job status added",
                   context.instanceJobs().isEmpty());
        assertEquals("DeploymentSpec is not stored", DeploymentSpec.empty, context.application().deploymentSpec());

        // Verify zone supports shared layer 4 and shared routing methods
        Set<RoutingMethod> routingMethods = tester.controller().routing().readEndpointsOf(context.deploymentIdIn(zone))
                .asList()
                .stream()
                .map(Endpoint::routingMethod)
                .collect(Collectors.toSet());
        assertEquals(routingMethods, Set.of(RoutingMethod.sharedLayer4));

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
    public void testDevDeploymentWithIncompatibleVersions() {
        Version version1 = new Version("7");
        Version version2 = new Version("7.5");
        Version version3 = new Version("8");
        var context = tester.newDeploymentContext();
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);
        tester.controllerTester().upgradeSystem(version2);
        ZoneId zone = ZoneId.from("dev", "us-east-1");

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).build());
        assertEquals(version2, context.deployment(zone).version());
        assertEquals(Optional.of(version1), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());

        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).majorVersion(8).build());
            fail("Should fail when specifying a major that does not yet exist");
        }
        catch (IllegalArgumentException e) {
            assertEquals("major 8 specified in deployment.xml, but no version on this major was found", e.getMessage());
        }

        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).build());
            fail("Should fail when compiled against a version which does not yet exist");
        }
        catch (IllegalArgumentException e) {
            assertEquals("no suitable platform version found for package compiled against 8", e.getMessage());
        }

        tester.controllerTester().upgradeSystem(version3);
        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).majorVersion(8).build());
            fail("Should fail when specifying a major which is incompatible with compile version");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Will not start a job with incompatible platform version (8) and compile versions (7)", e.getMessage());
        }

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).majorVersion(8).build());
        assertEquals(version3, context.deployment(zone).version());
        assertEquals(Optional.of(version3), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).build());
        assertEquals(version3, context.deployment(zone).version());
        assertEquals(Optional.of(version3), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());
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
                .region("us-west-1")
                .build();

        ZoneId zone = ZoneId.from(Environment.prod, RegionName.from("us-west-1"));
        context.submit(applicationPackage).runJob(zone, applicationPackage);
        tester.controller().applications().deactivate(context.instanceId(), zone);
        tester.controller().applications().deactivate(context.instanceId(), zone);
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
        context2.runJob(devZone, applicationPackage);
        assertTrue("Application deployed and activated",
                   tester.configServer().application(context2.instanceId(), devZone).get().activated());
        assertTrue("Provisions certificate also in zone with routing layer", certificate.apply(context2.instance()).isPresent());
    }

    @Test
    public void testDeployWithGlobalEndpointsInMultipleClouds() {
        tester.controllerTester().zoneRegistry().setZones(
                ZoneApiMock.fromId("test.us-west-1"),
                ZoneApiMock.fromId("staging.us-west-1"),
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
                .endpoint("default", "default", zone1.region().value(), zone2.region().value())
                .endpoint("east", "default", zone2.region().value())
                .region(zone1.region())
                .region(zone2.region())
                .build();

        // Zone 1 supports sharedLayer4
        tester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(zone1), RoutingMethod.sharedLayer4);
        // Zone 2 supports shared and exclusive
        tester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(zone2), RoutingMethod.exclusive);

        context.submit(applicationPackage).deploy();
        var expectedRecords = List.of(
                // The weighted record for zone 2's region
                new Record(Record.Type.ALIAS,
                           RecordName.from("application.tenant.us-east-3-w.vespa.oath.cloud"),
                           new WeightedAliasTarget(HostName.of("lb-0--tenant.application.default--prod.us-east-3"),
                                                   "dns-zone-1", ZoneId.from("prod.us-east-3"), 1).pack()),

                // The 'east' global endpoint, pointing to the weighted record for zone 2's region
                new Record(Record.Type.ALIAS,
                           RecordName.from("east.application.tenant.global.vespa.oath.cloud"),
                           new LatencyAliasTarget(HostName.of("application.tenant.us-east-3-w.vespa.oath.cloud"),
                                                  "dns-zone-1", ZoneId.from("prod.us-east-3")).pack()),

                // The zone-scoped endpoint pointing to zone 2 with exclusive routing
                new Record(Record.Type.CNAME,
                           RecordName.from("application.tenant.us-east-3.vespa.oath.cloud"),
                           RecordData.from("lb-0--tenant.application.default--prod.us-east-3.")));
        assertEquals(expectedRecords, List.copyOf(tester.controllerTester().nameService().records()));
    }

    @Test
    public void testDeploymentDirectRouting() {
        // Rotation-less system
        DeploymentTester tester = new DeploymentTester(new ControllerTester(new RotationsConfig.Builder().build(), main));
        var context = tester.newDeploymentContext();
        var zone1 = ZoneId.from("prod", "us-west-1");
        var zone2 = ZoneId.from("prod", "us-east-3");
        var zone3 = ZoneId.from("prod", "eu-west-1");
        tester.controllerTester().zoneRegistry()
              .exclusiveRoutingIn(ZoneApiMock.from(zone1), ZoneApiMock.from(zone2), ZoneApiMock.from(zone3));

        var applicationPackageBuilder = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .endpoint("default", "default")
                .endpoint("foo", "qrs")
                .endpoint("us", "default", zone1.region().value(), zone2.region().value())
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"));
        context.submit(applicationPackageBuilder.build()).deploy();

        // Deployment passes container endpoints to config server
        for (var zone : List.of(zone1, zone2)) {
            assertEquals("Expected container endpoints in " + zone,
                         Set.of("application.tenant.global.vespa.oath.cloud",
                                "foo.application.tenant.global.vespa.oath.cloud",
                                "us.application.tenant.global.vespa.oath.cloud"),
                         tester.configServer().containerEndpointNames(context.deploymentIdIn(zone)));
        }
        assertEquals("Expected container endpoints in " + zone3,
                     Set.of("application.tenant.global.vespa.oath.cloud",
                            "foo.application.tenant.global.vespa.oath.cloud"),
                     tester.configServer().containerEndpointNames(context.deploymentIdIn(zone3)));
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
        var db = new MockCuratorDb(tester.controller().system());
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

    @Test
    public void testTestPackageWarnings() {
        String deploymentXml = "<deployment version='1.0'>\n" +
                               "  <prod>\n" +
                               "    <region>us-west-1</region>\n" +
                               "  </prod>\n" +
                               "</deployment>\n";
        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(deploymentXml);
        byte[] testPackage = ApplicationPackage.filesZip(Map.of("tests/staging-test/foo.json", new byte[0]));
        var app = tester.newDeploymentContext();
        tester.jobs().submit(app.application().id(), Submission.basic(applicationPackage, testPackage), 1);
        assertEquals(List.of(new Notification(tester.clock().instant(),
                                              Type.testPackage,
                                              Level.warning,
                                              NotificationSource.from(app.application().id()),
                                              List.of("test package has staging tests, so it should also include staging setup",
                                                      "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"))),
                     tester.controller().notificationsDb().listNotifications(NotificationSource.from(app.application().id()), true));
    }

    @Test
    public void testCompileVersion() {
        DeploymentContext context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().region("us-west-1").build();
        TenantAndApplicationId application = TenantAndApplicationId.from(context.instanceId());

        // No deployments result in system version
        Version version0 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version0);
        assertEquals(version0, tester.applications().compileVersion(application, OptionalInt.empty()));
        context.submit(applicationPackage).deploy();

        // System is upgraded
        Version version1 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version1);
        assertEquals(version0, tester.applications().compileVersion(application, OptionalInt.empty()));

        // Application is upgraded and compile version is bumped
        tester.upgrader().maintain();
        context.deployPlatform(version1);
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));

        // A new major is released to the system
        Version version2 = Version.fromString("8.0");
        tester.controllerTester().upgradeSystem(version2);
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(8)));

        // The new major is marked as incompatible with older compile versions
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.of(8)));

        // Default major version is set to 8.
        tester.applications().setTargetMajorVersion(Optional.of(8));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.empty()));

        // Application sets target major to 7.
        tester.applications().lockApplicationOrThrow(application, locked -> tester.applications().store(locked.withMajorVersion(7)));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.of(8)));

        // Application sets target major to 8.
        tester.applications().lockApplicationOrThrow(application, locked -> tester.applications().store(locked.withMajorVersion(8)));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.empty()));
    }

    @Test
    public void testCloudAccount() {
        DeploymentContext context = tester.newDeploymentContext();
        ZoneId zone = ZoneId.from("prod", "us-west-1");
        String cloudAccount = "012345678912";
        var applicationPackage = new ApplicationPackageBuilder()
                .cloudAccount(cloudAccount)
                .region(zone.region())
                .build();
        try {
            context.submit(applicationPackage).deploy();
            fail("Expected exception"); // Account invalid for tenant
        } catch (IllegalArgumentException ignored) {}

        tester.controllerTester().flagSource().withListFlag(PermanentFlags.CLOUD_ACCOUNTS.id(), List.of(cloudAccount), String.class);
        context.submit(applicationPackage).deploy();
        assertEquals(cloudAccount, tester.controllerTester().configServer().cloudAccount(context.deploymentIdIn(zone)).get().value());
    }

    @Test
    public void testSubmitWithElementDeprecatedOnPreviousMajor() {
        DeploymentContext context = tester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .compileVersion(Version.fromString("8.1"))
                .region("us-west-1")
                .globalServiceId("qrs")
                .build();
        try {
            context.submit(applicationPackage).deploy();
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Element 'prod' contains attribute 'global-service-id' deprecated since major version 7"));
        }
    }

}
