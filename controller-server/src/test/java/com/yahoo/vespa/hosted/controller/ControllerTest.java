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
import com.yahoo.config.provision.CloudAccount;
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
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeploymentData;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
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
import com.yahoo.vespa.hosted.controller.deployment.JobController;
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
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.devUsEast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 * @author mpolden
 */
public class ControllerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    void testDeployment() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .explicitEnvironment(Environment.dev, Environment.perf)
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // staging job - succeeding
        Version version1 = tester.configServer().initialVersion();
        var context = tester.newDeploymentContext();
        context.submit(applicationPackage);
        RevisionId id = RevisionId.forProduction(1);
        Version compileVersion = new Version("6.1");
        assertEquals(new ApplicationVersion(id, Optional.of(DeploymentContext.defaultSourceRevision), Optional.of("a@b"), Optional.of(compileVersion), Optional.empty(), Optional.of(Instant.ofEpochSecond(1)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, false, Optional.empty(), Optional.empty(), 0),
                     context.application().revisions().get(context.instance().change().revision().get()),
                     "Application version is known from completion of initial job");
        context.runJob(systemTest);
        context.runJob(stagingTest);

        RevisionId applicationVersion = context.instance().change().revision().get();
        assertTrue(applicationVersion.isProduction(), "Application version has been set during deployment");

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
            assertEquals("deployment-removal: application instance 'tenant.application.default' is deployed in us-west-1, " +
                         "but this instance and region combination is removed from deployment.xml. " +
                         ValidationOverrides.toAllowMessage(ValidationId.deploymentRemoval),
                         e.getMessage());
        }
        assertNotNull(context.instance().deployments().get(productionUsWest1.zone()),
                      "Zone was not removed");

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .region("us-east-3")
                .build();
        context.submit(applicationPackage);
        assertNull(context.instance().deployments().get(productionUsWest1.zone()),
                "Zone was removed");
        assertNull(context.instanceJobs().get(productionUsWest1), "Deployment job was removed");

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
    void testPackagePruning() {
        DeploymentContext app = tester.newDeploymentContext().submit().deploy();
        RevisionId revision1 = app.lastSubmission().get();
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision1.number()));

        app.submit().deploy();
        RevisionId revision2 = app.lastSubmission().get();
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision1.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision2.number()));

        // Revision 1 is marked as obsolete now
        app.submit().deploy();
        RevisionId revision3 = app.lastSubmission().get();
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision1.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision2.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision3.number()));

        // Time advances, and revision 2 is marked as obsolete now
        tester.clock().advance(JobController.obsoletePackageExpiry);
        app.submit().deploy();
        RevisionId revision4 = app.lastSubmission().get();
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision1.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision2.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision3.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision4.number()));

        // Time advances, and revision is now old enough to be pruned
        tester.clock().advance(Duration.ofMillis(1));
        app.submit().deploy();
        RevisionId revision5 = app.lastSubmission().get();
        assertFalse(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision1.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision2.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision3.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision4.number()));
        assertTrue(tester.controllerTester().serviceRegistry().applicationStore()
                         .hasBuild(app.instanceId().tenant(), app.instanceId().application(), revision5.number()));
    }

    @Test
    void testGlobalRotationStatus() {
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
    void testDnsUpdatesForGlobalEndpoint() {
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
            assertEquals(dnsNames, actualDnsNames, "Global DNS names for " + instance);
        });
    }

    @Test
    void testDnsUpdatesForGlobalEndpointLegacySyntax() {
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
            assertEquals(Set.of("rotation-id-01",
                            "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(deployment.zone())),
                    "Rotation names are passed to config server in " + deployment.zone());
        }
        context.flushDnsUpdates();
        assertEquals(1, tester.controllerTester().nameService().records().size());

        Optional<Record> record = tester.controllerTester().findCname("app1.tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        List<String> globalDnsNames = tester.controller().routing().readDeclaredEndpointsOf(context.instanceId())
                .scope(Endpoint.Scope.global)
                .sortedBy(comparing(Endpoint::dnsName))
                .mapToList(Endpoint::dnsName);
        assertEquals(List.of("app1.tenant1.global.vespa.oath.cloud"),
                globalDnsNames);
    }

    @Test
    void testDnsUpdatesForMultipleGlobalEndpoints() {
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
            assertEquals(ZoneId.from("prod.us-west-1").equals(deployment.zone()) ? west : notWest,
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(deployment.zone())),
                    "Rotation names are passed to config server in " + deployment.zone());
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
    void testDnsUpdatesForGlobalEndpointChanges() {
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
                    Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
            ,
                    "Zone " + zone + " is a member of global endpoint");
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
                    Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
            ,
                    "Zone " + zone + " is a member of global endpoint");
        }
        assertEquals(
                Set.of("rotation-id-02", "east.app1.tenant1.global.vespa.oath.cloud"),
                tester.configServer().containerEndpointNames(context.deploymentIdIn(east))
        ,
                "Zone " + east + " is a member of global endpoint");

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
                    zone.equals(east)
                            ? Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud",
                            "rotation-id-02", "east.app1.tenant1.global.vespa.oath.cloud")
                            : Set.of("rotation-id-01", "app1.tenant1.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone))
            ,
                    "Zone " + zone + " is a member of global endpoint");
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
    void testUnassignRotations() {
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
    void testDnsUpdatesWithChangeInRotationAssignment() {
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
                assertTrue(tester.controller().routing().rotations().availableRotations(lock)
                                .containsKey(new RotationId("rotation-id-01")),
                        "Rotation is unassigned");
            }
            context.flushDnsUpdates();

            // Record is removed
            Optional<Record> record = tester.controllerTester().findCname(dnsName1);
            assertTrue(record.isEmpty(), dnsName1 + " is removed");
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
    void testDnsUpdatesForApplicationEndpoint() {
        ApplicationId beta = ApplicationId.from("tenant1", "app1", "beta");
        ApplicationId main = ApplicationId.from("tenant1", "app1", "main");
        var context = tester.newDeploymentContext(beta);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("beta,main")
                .region("us-east-3")
                .region("us-west-1")
                .region("aws-us-east-1a")
                .region("aws-us-east-1b")
                .applicationEndpoint("a", "default",
                                     Map.of("aws-us-east-1a", Map.of(beta.instance(), 2,
                                                                     main.instance(), 8),
                                            "aws-us-east-1b", Map.of(main.instance(), 1)))
                .applicationEndpoint("b", "default", "aws-us-east-1a",
                                     Map.of(beta.instance(), 1,
                                            main.instance(), 1))
                .applicationEndpoint("c", "default", "aws-us-east-1b",
                                     Map.of(beta.instance(), 4))
                .applicationEndpoint("d", "default", "us-west-1",
                                     Map.of(main.instance(), 7,
                                            beta.instance(), 3))
                .applicationEndpoint("e", "default", "us-east-3",
                                     Map.of(main.instance(), 3))
                .build();
        context.submit(applicationPackage).deploy();

        ZoneId east3 = ZoneId.from("prod", "us-east-3");
        ZoneId west1 = ZoneId.from("prod", "us-west-1");
        ZoneId east1a = ZoneId.from("prod", "aws-us-east-1a");
        ZoneId east1b = ZoneId.from("prod", "aws-us-east-1b");
        // Expected container endpoints are passed to each deployment
        Map<DeploymentId, Map<List<String>, Integer>> deploymentEndpoints = Map.of(
                new DeploymentId(beta, east3), Map.of(),
                new DeploymentId(main, east3), Map.of(List.of("e.app1.tenant1.a.vespa.oath.cloud", "e.app1.tenant1.us-east-3-r.vespa.oath.cloud"), 3),
                new DeploymentId(beta, west1), Map.of(List.of("d.app1.tenant1.a.vespa.oath.cloud", "d.app1.tenant1.us-west-1-r.vespa.oath.cloud"), 3),
                new DeploymentId(main, west1), Map.of(List.of("d.app1.tenant1.a.vespa.oath.cloud", "d.app1.tenant1.us-west-1-r.vespa.oath.cloud"), 7),
                new DeploymentId(beta, east1a), Map.of(List.of("a.app1.tenant1.a.vespa.oath.cloud", "a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"), 2,
                                                       List.of("b.app1.tenant1.a.vespa.oath.cloud", "b.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"), 1),
                new DeploymentId(main, east1a), Map.of(List.of("a.app1.tenant1.a.vespa.oath.cloud", "a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"), 8,
                                                       List.of("b.app1.tenant1.a.vespa.oath.cloud", "b.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"), 1),
                new DeploymentId(beta, east1b), Map.of(List.of("c.app1.tenant1.a.vespa.oath.cloud", "c.app1.tenant1.aws-us-east-1b-r.vespa.oath.cloud"), 4),
                new DeploymentId(main, east1b), Map.of(List.of("a.app1.tenant1.a.vespa.oath.cloud", "a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"), 1)
        );
        deploymentEndpoints.forEach((deployment, endpoints) -> {
            Set<ContainerEndpoint> expected = endpoints.entrySet().stream()
                                                       .map(kv -> new ContainerEndpoint("default", "application",
                                                                                        kv.getKey(),
                                                                                        OptionalInt.of(kv.getValue()),
                                                                                        tester.controller().zoneRegistry().routingMethod(deployment.zoneId())))
                                                       .collect(Collectors.toSet());
            assertEquals(expected,
                         tester.configServer().containerEndpoints().get(deployment),
                         "Endpoint names for " + deployment + " are passed to config server");
        });
        context.flushDnsUpdates();

        // DNS records are created for each endpoint
        Set<Record> records = tester.controllerTester().nameService().records();
        assertEquals(new TreeSet<>(Set.of(new Record(Record.Type.CNAME,
                                                     RecordName.from("beta.app1.tenant1.aws-us-east-1a.vespa.oath.cloud"),
                                                     RecordData.from("lb-0--tenant1.app1.beta--prod.aws-us-east-1a.")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("beta.app1.tenant1.aws-us-east-1b.vespa.oath.cloud"),
                                                     RecordData.from("lb-0--tenant1.app1.beta--prod.aws-us-east-1b.")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("main.app1.tenant1.aws-us-east-1a.vespa.oath.cloud"),
                                                     RecordData.from("lb-0--tenant1.app1.main--prod.aws-us-east-1a.")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("main.app1.tenant1.aws-us-east-1b.vespa.oath.cloud"),
                                                     RecordData.from("lb-0--tenant1.app1.main--prod.aws-us-east-1b.")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/2")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/8")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1b/dns-zone-1/prod.aws-us-east-1b/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("b.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("b.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("c.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1b/dns-zone-1/prod.aws-us-east-1b/4")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("d.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("vip.prod.us-west-1.")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("e.app1.tenant1.a.vespa.oath.cloud"),
                                                     RecordData.from("vip.prod.us-east-3.")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/2")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/8")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("a.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1b/dns-zone-1/prod.aws-us-east-1b/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("b.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("b.app1.tenant1.aws-us-east-1a-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.main--prod.aws-us-east-1a/dns-zone-1/prod.aws-us-east-1a/1")),
                                          new Record(Record.Type.ALIAS,
                                                     RecordName.from("c.app1.tenant1.aws-us-east-1b-r.vespa.oath.cloud"),
                                                     RecordData.from("weighted/lb-0--tenant1.app1.beta--prod.aws-us-east-1b/dns-zone-1/prod.aws-us-east-1b/4")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("d.app1.tenant1.us-west-1-r.vespa.oath.cloud"),
                                                     RecordData.from("vip.prod.us-west-1.")),
                                          new Record(Record.Type.CNAME,
                                                     RecordName.from("e.app1.tenant1.us-east-3-r.vespa.oath.cloud"),
                                                     RecordData.from("vip.prod.us-east-3.")))),
                     new TreeSet<>(records));
        List<String> endpointDnsNames = tester.controller().routing().declaredEndpointsOf(context.application())
                                              .scope(Endpoint.Scope.application)
                                              .sortedBy(comparing(Endpoint::dnsName))
                                              .mapToList(Endpoint::dnsName);
        assertEquals(List.of("a.app1.tenant1.a.vespa.oath.cloud",
                             "b.app1.tenant1.a.vespa.oath.cloud",
                             "c.app1.tenant1.a.vespa.oath.cloud",
                             "d.app1.tenant1.a.vespa.oath.cloud",
                             "e.app1.tenant1.a.vespa.oath.cloud"),
                     endpointDnsNames);
    }

    @Test
    void testDevDeployment() {
        // A package without deployment.xml is considered valid
        ApplicationPackage applicationPackage = new ApplicationPackage(new byte[0]);

        // Create application
        var context = tester.newDeploymentContext();
        ZoneId zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().zoneRegistry()
                .setRoutingMethod(ZoneApiMock.from(zone), RoutingMethod.sharedLayer4);

        // Deploy
        context.runJob(zone, applicationPackage);
        assertTrue(tester.configServer().application(context.instanceId(), zone).get().activated(),
                "Application deployed and activated");
        assertTrue(context.instanceJobs().isEmpty(),
                "No job status added");
        assertEquals(DeploymentSpec.empty, context.application().deploymentSpec(), "DeploymentSpec is not stored");

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
    void testDevDeploymentWithIncompatibleVersions() {
        Version version1 = new Version("7");
        Version version2 = new Version("7.5");
        Version version3 = new Version("8");
        var context = tester.newDeploymentContext();
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);
        tester.controllerTester().upgradeSystem(version2);
        tester.newDeploymentContext("keep", "v2", "alive").submit().deploy(); // TODO jonmv: remove
        ZoneId zone = ZoneId.from("dev", "us-east-1");

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).build());
        assertEquals(version2, context.deployment(zone).version());
        assertEquals(Optional.of(version1), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());

        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).majorVersion(8).build());
            fail("Should fail when specifying a major that does not yet exist");
        }
        catch (IllegalArgumentException e) {
            assertEquals("no platforms were found for major version 8 specified in deployment.xml", e.getMessage());
        }

        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).build());
            fail("Should fail when compiled against a version which is only compatible with not-yet-existent versions");
        }
        catch (IllegalArgumentException e) {
            assertEquals("no platforms are compatible with compile version 8", e.getMessage());
        }

        tester.controllerTester().upgradeSystem(version3);
        try {
            context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version1).majorVersion(8).build());
            fail("Should fail when specifying a major which is incompatible with compile version");
        }
        catch (IllegalArgumentException e) {
            assertEquals("no platforms on major version 8 specified in deployment.xml are compatible with compile version 7", e.getMessage());
        }

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).majorVersion(8).build());
        assertEquals(version3, context.deployment(zone).version());
        assertEquals(Optional.of(version3), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());

        context.runJob(zone, new ApplicationPackageBuilder().compileVersion(version3).build());
        assertEquals(version3, context.deployment(zone).version());
        assertEquals(Optional.of(version3), context.application().revisions().get(context.deployment(zone).revision()).compileVersion());
    }

    @Test
    void testSuspension() {
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
    void testDeletingApplicationThatHasAlreadyBeenDeleted() {
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
    void testDeployApplicationWithWarnings() {
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
    void testDeploySelectivelyProvisionsCertificate() {
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
        assertTrue(cert.isPresent(), "Provisions certificate in " + Environment.prod);
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
        assertTrue(tester.configServer().application(context2.instanceId(), devZone).get().activated(),
                "Application deployed and activated");
        assertTrue(certificate.apply(context2.instance()).isPresent(), "Provisions certificate also in zone with routing layer");
    }

    @Test
    void testDeployWithGlobalEndpointsInMultipleClouds() {
        tester.controllerTester().zoneRegistry().setZones(
                ZoneApiMock.fromId("test.us-west-1"),
                ZoneApiMock.fromId("staging.us-west-1"),
                ZoneApiMock.fromId("prod.us-west-1"),
                ZoneApiMock.newBuilder().with(CloudName.AWS).withId("prod.aws-us-east-1").build()
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
    void testDeployWithoutSourceRevision() {
        var context = tester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .region("us-west-1")
                .build();

        // Submit without source revision
        context.submit(applicationPackage, Optional.empty())
                .deploy();
        assertEquals(1, context.instance().deployments().size(), "Deployed application");
    }

    @Test
    void testDeployWithGlobalEndpointsAndMultipleRoutingMethods() {
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
                                "dns-zone-1", "prod.us-east-3", 1).pack()),

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
    void testDeploymentDirectRouting() {
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
            assertEquals(Set.of("application.tenant.global.vespa.oath.cloud",
                            "foo.application.tenant.global.vespa.oath.cloud",
                            "us.application.tenant.global.vespa.oath.cloud"),
                    tester.configServer().containerEndpointNames(context.deploymentIdIn(zone)),
                    "Expected container endpoints in " + zone);
        }
        assertEquals(Set.of("application.tenant.global.vespa.oath.cloud",
                        "foo.application.tenant.global.vespa.oath.cloud"),
                tester.configServer().containerEndpointNames(context.deploymentIdIn(zone3)),
                "Expected container endpoints in " + zone3);
    }

    @Test
    void testChangeEndpointCluster() {
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
    void testZoneEndpointChanges() {
        DeploymentContext app = tester.newDeploymentContext();
        // Set up app with default settings.
        app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                               <deployment>
                                                                 <prod>
                                                                   <region>us-east-3</region>
                                                                 </prod>
                                                               </deployment>"""));

        assertEquals("zone-endpoint-change: application 'tenant.application' has a public endpoint for " +
                     "cluster 'foo' in 'us-east-3', but the new deployment spec disables this. " +
                     "To allow this add <allow until='yyyy-mm-dd'>zone-endpoint-change</allow> to validation-overrides.xml, " +
                     "see https://docs.vespa.ai/en/reference/validation-overrides.html",
                     assertThrows(IllegalArgumentException.class,
                                  () -> app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                                                               <deployment>
                                                                                                 <prod>
                                                                                                   <region>us-east-3</region>
                                                                                                 </prod>
                                                                                                 <endpoints>
                                                                                                   <endpoint type='zone' container-id='foo' enabled='false' />
                                                                                                 </endpoints>
                                                                                               </deployment>""")))
                             .getMessage());

        // Disabling endpoints is OK with override.
        app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                               <deployment>
                                                                 <prod>
                                                                   <region>us-east-3</region>
                                                                 </prod>
                                                                 <endpoints>
                                                                   <endpoint type='zone' container-id='foo' enabled='false' />
                                                                 </endpoints>
                                                               </deployment>""",
                                                               ValidationId.zoneEndpointChange));

        // Enabling endpoints again is OK, as is adding a private endpoint with some URN.
        app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                               <deployment>
                                                                 <prod>
                                                                   <region>us-east-3</region>
                                                                 </prod>
                                                                 <endpoints>
                                                                   <endpoint type='private' container-id='foo'>
                                                                     <allow with='aws-private-link' arn='yarn' />
                                                                   </endpoint>
                                                                 </endpoints>
                                                               </deployment>""",
                                                               ValidationId.zoneEndpointChange));

        // Changing URNs is guarded.
        assertEquals("zone-endpoint-change: application 'tenant.application' allows access to cluster " +
                     "'foo' in 'us-east-3' to ['yarn' through 'aws-private-link'], " +
                     "but does not include all these in the new deployment spec. " +
                     "Deploying with the new settings will allow access to ['yarn' through 'gcp-service-connect']. " +
                     "To allow this add <allow until='yyyy-mm-dd'>zone-endpoint-change</allow> to validation-overrides.xml, " +
                     "see https://docs.vespa.ai/en/reference/validation-overrides.html",
                     assertThrows(IllegalArgumentException.class,
                                  () -> app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                                                               <deployment>
                                                                                                 <prod>
                                                                                                   <region>us-east-3</region>
                                                                                                 </prod>
                                                                                                 <endpoints>
                                                                                                   <endpoint type='private' container-id='foo'>
                                                                                                     <allow with='gcp-service-connect' project='yarn' />
                                                                                                   </endpoint>
                                                                                                 </endpoints>
                                                                                               </deployment>""")))
                             .getMessage());

        // Changing cluster, effectively removing old URNs, is also guarded.
        assertEquals("zone-endpoint-change: application 'tenant.application' allows access to cluster 'foo' in 'us-east-3' to " +
                     "['yarn' through 'aws-private-link'], but does not include all these in the new deployment spec. " +
                     "Deploying with the new settings will allow access to no one",
                     assertThrows(IllegalArgumentException.class,
                                  () -> app.submit(ApplicationPackageBuilder.fromDeploymentXml("""
                                                                                               <deployment>
                                                                                                 <prod>
                                                                                                   <region>us-east-3</region>
                                                                                                 </prod>
                                                                                                 <endpoints>
                                                                                                   <endpoint type='private' container-id='bar'>
                                                                                                     <allow with='aws-private-link' arn='yarn' />
                                                                                                   </endpoint>
                                                                                                 </endpoints>
                                                                                               </deployment>""")))
                             .getMessage());
    }


        @Test
    void testReadableApplications() {
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
    void testClashingEndpointIdAndInstanceName() {
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
    void testTestPackageWarnings() {
        String deploymentXml = "<deployment version='1.0'>\n" +
                "  <prod>\n" +
                "    <region>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>\n";
        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(deploymentXml);
        byte[] testPackage = ApplicationPackage.filesZip(Map.of("tests/staging-test/foo.json", new byte[0]));
        var app = tester.newDeploymentContext();
        tester.jobs().submit(app.application().id(), new Submission(applicationPackage, testPackage, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH, 0), 1);
        assertEquals(List.of(new Notification(tester.clock().instant(),
                        Type.testPackage,
                        Level.warning,
                        NotificationSource.from(app.application().id()),
                        List.of("test package has staging tests, so it should also include staging setup",
                                "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"))),
                tester.controller().notificationsDb().listNotifications(NotificationSource.from(app.application().id()), true));
    }

    @Test
    void testCompileVersion() {
        DeploymentContext context = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().region("us-west-1").build();
        TenantAndApplicationId application = TenantAndApplicationId.from(context.instanceId());

        // No deployments result in system version
        Version version0 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().overrideConfidence(version0, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version0, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version0, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals("this system has no available versions on specified major: 8",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.applications().compileVersion(application, OptionalInt.of(8)))
                             .getMessage());
        context.submit(applicationPackage).deploy();

        // System is upgraded
        Version version1 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().overrideConfidence(version1, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version0, tester.applications().compileVersion(application, OptionalInt.empty()));

        // Application is upgraded and compile version is bumped
        tester.upgrader().maintain();
        context.deployPlatform(version1);
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));

        DeploymentContext legacyApp = tester.newDeploymentContext("avoid", "gc", "default").submit().deploy();
        TenantAndApplicationId newApp = TenantAndApplicationId.from("new", "app");

        // A new major is released to the system
        Version version2 = Version.fromString("8.0");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals("this system has no available versions on specified major: 8",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.applications().compileVersion(application, OptionalInt.of(8)))
                             .getMessage());

        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(8)));
        assertEquals(version2, tester.applications().compileVersion(newApp, OptionalInt.empty()));

        // The new major is marked as incompatible with older compile versions
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.of(8)));
        assertEquals(version2, tester.applications().compileVersion(newApp, OptionalInt.empty()));

        // The only version on major 8 has low confidence.
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals("this system has no available versions on specified major: 8",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.applications().compileVersion(application, OptionalInt.of(8)))
                             .getMessage());
        assertEquals(version1, tester.applications().compileVersion(newApp, OptionalInt.empty()));
        assertEquals(version1, tester.applications().compileVersion(newApp, OptionalInt.empty()));

        // Version on major 8 has normal confidence again
        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.controllerTester().computeVersionStatus();

        // Application upgrades to major 8; major version from deployment spec should cause a downgrade.
        context.submit(new ApplicationPackageBuilder().region("us-west-1").compileVersion(version2).build()).deploy();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.empty()));

        // Reduced confidence should not cause a downgrade.
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.of(8)));

        // All versions on new major having broken confidence makes it all fail for upgraded apps, but this shouldn't happen in practice.
        tester.upgrader().overrideConfidence(version2, Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals("no suitable, released compile version exists",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.applications().compileVersion(application, OptionalInt.empty()))
                             .getMessage());
        assertEquals("no suitable, released compile version exists for specified major: 8",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.applications().compileVersion(application, OptionalInt.of(8)))
                             .getMessage());

        // Major versions are not incompatible anymore, so the old compile version should work again.
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of(), String.class);
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(8)));

        // Simply reduced confidence shouldn't cause any changes.
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.applications().compileVersion(application, OptionalInt.of(7)));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.empty()));
        assertEquals(version2, tester.applications().compileVersion(application, OptionalInt.of(8)));
    }

    @Test
    void testCloudAccount() {
        DeploymentContext context = tester.newDeploymentContext();
        ZoneId devZone = devUsEast1.zone();
        ZoneId prodZone = productionUsWest1.zone();
        String cloudAccount = "012345678912";
        var applicationPackage = new ApplicationPackageBuilder()
                .cloudAccount(cloudAccount)
                .region(prodZone.region())
                .build();

        // Submission fails because cloud account is not declared for this tenant
        assertEquals("cloud accounts [012345678912] are not valid for tenant tenant",
                     assertThrows(IllegalArgumentException.class,
                                  () -> context.submit(applicationPackage))
                             .getMessage());
        assertEquals("cloud accounts [012345678912] are not valid for tenant tenant",
                     assertThrows(IllegalArgumentException.class,
                                  () -> context.runJob(devUsEast1, applicationPackage))
                             .getMessage());

        // Deployment fails because zone is not configured in requested cloud account
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.CLOUD_ACCOUNTS.id(), List.of(cloudAccount), String.class);
        assertEquals("Zone test.us-east-1 is not configured in requested cloud account '012345678912'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> context.submit(applicationPackage))
                             .getMessage());
        assertEquals("Zone dev.us-east-1 is not configured in requested cloud account '012345678912'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> context.runJob(devUsEast1, applicationPackage))
                             .getMessage());

        // Deployment to prod succeeds once all zones are configured in requested account
        tester.controllerTester().zoneRegistry().configureCloudAccount(CloudAccount.from(cloudAccount),
                                                                       systemTest.zone(),
                                                                       stagingTest.zone(),
                                                                       prodZone);
        context.submit(applicationPackage).deploy();

        // Dev zone is added as a configured zone and deployment succeeds
        tester.controllerTester().zoneRegistry().configureCloudAccount(CloudAccount.from(cloudAccount), devZone);
        context.runJob(devZone, applicationPackage);

        // All deployments use the custom account
        for (var zoneId : List.of(systemTest.zone(), stagingTest.zone(), devZone, prodZone)) {
            assertEquals(cloudAccount, tester.controllerTester().configServer()
                                             .cloudAccount(context.deploymentIdIn(zoneId))
                                             .get().value());
        }
    }

    @Test
    void testCloudAccountWithDefaultOverride() {
        var context = tester.newDeploymentContext();
        var prodZone1 = productionUsEast3.zone();
        var prodZone2 = productionUsWest1.zone();
        var cloudAccount = "012345678912";
        var application = new ApplicationPackageBuilder()
                .cloudAccount(cloudAccount)
                .region(prodZone1.region())
                .region(prodZone2.region(), "default")
                .build();

        // Allow use of custom account (test, staging and zone 1)
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.CLOUD_ACCOUNTS.id(), List.of(cloudAccount), String.class);

        // Deployment to prod succeeds once all zones are configured in requested account
        tester.controllerTester().zoneRegistry().configureCloudAccount(CloudAccount.from(cloudAccount),
                systemTest.zone(),
                stagingTest.zone(),
                prodZone1);

        context.submit(application).deploy();

        assertEquals(cloudAccount, tester.controllerTester().configServer().cloudAccount(context.deploymentIdIn(prodZone1)).get().value());
        assertEquals(Optional.empty(), tester.controllerTester().configServer().cloudAccount(context.deploymentIdIn(prodZone2)));
    }

    @Test
    void testSubmitWithElementDeprecatedOnPreviousMajor() {
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

    @Test
    void testDeactivateDeploymentUnknownByController() {
        DeploymentContext context = tester.newDeploymentContext();
        DeploymentId deployment = context.deploymentIdIn(ZoneId.from("prod", "us-west-1"));
        DeploymentData deploymentData = new DeploymentData(deployment.applicationId(), deployment.zoneId(), InputStream::nullInputStream, Version.fromString("6.1"),
                                                           Set.of(), Optional::empty, Optional.empty(), Optional.empty(),
                                                           Quota::unlimited, List.of(), List.of(), Optional::empty, false);
        tester.configServer().deploy(deploymentData);
        assertTrue(tester.configServer().application(deployment.applicationId(), deployment.zoneId()).isPresent());
        tester.controller().applications().deactivate(deployment.applicationId(), deployment.zoneId());
        assertFalse(tester.configServer().application(deployment.applicationId(), deployment.zoneId()).isPresent());
    }

    @Test
    void testVerifyPlan() {
        DeploymentId deployment = tester.newDeploymentContext().deploymentIdIn(ZoneId.from("prod", "us-west-1"));
        TenantName tenant = deployment.applicationId().tenant();

        tester.controller().serviceRegistry().billingController().setPlan(tenant, PlanRegistryMock.nonePlan.id(), false, false);
        try {
            tester.controller().applications().verifyPlan(tenant);
            fail("should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Tenant 'tenant' has a plan 'None Plan - for testing purposes' with zero quota, not allowed to deploy. " +
                                 "See https://cloud.vespa.ai/support", e.getMessage());
        }
    }

}
