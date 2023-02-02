// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record.Type;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.DnsChallenge;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.State;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mortent
 * @author mpolden
 */
public class RoutingPoliciesTest {

    private static final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private static final ZoneId zone3 = ZoneId.from("prod", "aws-us-east-1a");
    private static final ZoneId zone4 = ZoneId.from("prod", "aws-us-east-1b");
    private static final ZoneId zone5 = ZoneId.from("prod", "north");
    private static final ZoneId zone6 = ZoneId.from("prod", "south");

    private static final ApplicationPackage applicationPackage = applicationPackageBuilder().region(zone1.region())
                                                                                            .region(zone2.region())
                                                                                            .build();

    @Test
    void global_routing_policies() {
        var tester = new RoutingPoliciesTester();
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");
        int clustersPerZone = 2;
        int numberOfDeployments = 2;
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);

        // Creates alias records
        context1.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context1.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r1"), 0, zone1);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r2"), 1, zone1, zone2);
        assertEquals(numberOfDeployments * clustersPerZone,
                tester.policiesOf(context1.instance().id()).size(),
                "Routing policy count is equal to cluster count");

        // Applications gains a new deployment
        ApplicationPackage applicationPackage2 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        numberOfDeployments++;
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone3);
        context1.submit(applicationPackage2).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        // Endpoints are updated to contain cluster in new deployment
        tester.assertTargets(context1.instanceId(), EndpointId.of("r0"), 0, zone1, zone2, zone3);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r1"), 0, zone1);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r2"), 1, zone1, zone2, zone3);

        // Another application is deployed with a single cluster and global endpoint
        var endpoint4 = "r0.app2.tenant1.global.vespa.oath.cloud";
        tester.provisionLoadBalancers(1, context2.instanceId(), zone1, zone2);
        var applicationPackage3 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .build();
        context2.submit(applicationPackage3).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context2.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);

        // A deployment of app2 is removed
        var applicationPackage4 = applicationPackageBuilder()
                .region(zone1.region())
                .endpoint("r0", "c0")
                .allow(ValidationId.globalEndpointChange)
                .allow(ValidationId.deploymentRemoval)
                .build();
        context2.submit(applicationPackage4).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context2.instanceId(), EndpointId.of("r0"), 0, zone1);
        assertEquals(1, tester.policiesOf(context2.instanceId()).size());

        // All endpoints for app1 are removed
        ApplicationPackage applicationPackage5 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context1.submit(applicationPackage5).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context1.instanceId(), EndpointId.of("r0"), 0);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r1"), 0);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r2"), 0);
        var policies = tester.policiesOf(context1.instanceId());
        assertEquals(clustersPerZone * numberOfDeployments, policies.size());
        assertTrue(policies.asList().stream().allMatch(policy -> policy.instanceEndpoints().isEmpty()),
                "Rotation membership is removed from all policies");
        assertEquals(1, tester.aliasDataOf(endpoint4).size(), "Rotations for " + context2.application() + " are not removed");
    }

    @Test
    void global_routing_policies_with_duplicate_region() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        int clustersPerZone = 2;
        int numberOfDeployments = 3;
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone3.region())
                .region(zone4.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c1")
                .build();
        tester.provisionLoadBalancers(clustersPerZone, context.instanceId(), zone1, zone3, zone4);

        // Creates alias records
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone3, zone4);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 1, zone1, zone3, zone4);
        assertEquals(numberOfDeployments * clustersPerZone,
                tester.policiesOf(context.instance().id()).size(),
                "Routing policy count is equal to cluster count");

        // A zone in shared region is set out
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone4), RoutingStatus.Value.out,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();

        // Weight of inactive zone is set to zero
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, ImmutableMap.of(zone1, 1L,
                zone3, 1L,
                zone4, 0L));

        // Other zone in shared region is set out. Entire record group for the region is removed as all zones in the
        // region are out (weight sum = 0)
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone3), RoutingStatus.Value.out,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, ImmutableMap.of(zone1, 1L));

        // Everything is set back in
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone3), RoutingStatus.Value.in,
                RoutingStatus.Agent.tenant);
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone4), RoutingStatus.Value.in,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, ImmutableMap.of(zone1, 1L,
                zone3, 1L,
                zone4, 1L));
    }

    @Test
    void global_routing_policies_legacy_global_service_id() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        int clustersPerZone = 2;
        int numberOfDeployments = 2;
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .globalServiceId("c0")
                .build();
        tester.provisionLoadBalancers(clustersPerZone, context.instanceId(), zone1, zone2);

        // Creates alias records
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context.instanceId(), EndpointId.defaultId(), 0, zone1, zone2);
        assertEquals(numberOfDeployments * clustersPerZone,
                tester.policiesOf(context.instance().id()).size(),
                "Routing policy count is equal to cluster count");
    }

    @Test
    void zone_routing_policies() {
        zone_routing_policies(false);
        zone_routing_policies(true);
    }

    private void zone_routing_policies(boolean sharedRoutingLayer) {
        var tester = new RoutingPoliciesTester();
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");

        // Deploy application
        int clustersPerZone = 2;
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), sharedRoutingLayer, zone1, zone2);
        context1.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        // Deployment creates records and policies for all clusters in all zones
        Set<String> expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(4, tester.policiesOf(context1.instanceId()).size());

        // Next deploy does nothing
        context1.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(4, tester.policiesOf(context1.instanceId()).size());

        // Add 1 cluster in each zone and deploy
        tester.provisionLoadBalancers(clustersPerZone + 1, context1.instanceId(), sharedRoutingLayer, zone1, zone2);
        context1.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(6, tester.policiesOf(context1.instanceId()).size());

        // Deploy another application
        tester.provisionLoadBalancers(clustersPerZone, context2.instanceId(), sharedRoutingLayer, zone1, zone2);
        context2.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c0.app2.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app2.tenant1.us-central-1.vespa.oath.cloud",
                "c0.app2.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app2.tenant1.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords.stream().sorted().toList(), tester.recordNames().stream().sorted().toList());
        assertEquals(4, tester.policiesOf(context2.instanceId()).size());

        // Deploy removes cluster from app1
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), sharedRoutingLayer, zone1, zone2);
        context1.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c0.app2.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app2.tenant1.us-central-1.vespa.oath.cloud",
                "c0.app2.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app2.tenant1.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, tester.recordNames());

        // Remove app2 completely
        tester.controllerTester().controller().applications().requireInstance(context2.instanceId()).deployments().keySet()
              .forEach(zone -> {
                  tester.controllerTester().configServer().removeLoadBalancers(context2.instanceId(), zone);
                  tester.controllerTester().controller().applications().deactivate(context2.instanceId(), zone);
              });
        context2.flushDnsUpdates();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, tester.recordNames());
        assertTrue(tester.routingPolicies().read(context2.instanceId()).isEmpty(), "Removes stale routing policies " + context2.application());
        assertEquals(4, tester.routingPolicies().read(context1.instanceId()).size(), "Keeps routing policies for " + context1.application());
    }

    @Test
    void zone_routing_policies_without_dns_update() {
        var tester = new RoutingPoliciesTester(new DeploymentTester(), false);
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        tester.provisionLoadBalancers(1, context.instanceId(), true, zone1, zone2);
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        assertEquals(0, tester.controllerTester().controller().curator().readNameServiceQueue().requests().size());
        assertEquals(Set.of(), tester.recordNames());
        assertEquals(2, tester.policiesOf(context.instanceId()).size());
    }

    @Test
    void global_routing_policies_in_rotationless_system() {
        var tester = new RoutingPoliciesTester(SystemName.Public);
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        List<ZoneId> prodZones = tester.controllerTester().controller().zoneRegistry().zones().all().in(Environment.prod).ids();
        ZoneId zone1 = prodZones.get(0);
        ZoneId zone2 = prodZones.get(1);
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);

        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region().value())
                .endpoint("r0", "c0")
                .trustDefaultCertificate()
                .build();
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);
        assertTrue(context.application().instances().values().stream()
                .map(Instance::rotations)
                .allMatch(List::isEmpty), "No rotations assigned");
    }

    @Test
    void cross_cloud_policies() {
        var tester = new RoutingPoliciesTester(SystemName.Public);
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        var zone1 = ZoneId.from("prod", "aws-us-east-1c");
        var zone2 = ZoneId.from("prod", "gcp-us-south1-b");
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);

        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region().value())
                .region(zone2.region().value())
                .endpoint("r0", "c0")
                .trustDefaultCertificate()
                .build();
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        List<String> expectedRecords = List.of("c0.app1.tenant1.aws-us-east-1c.z.vespa-app.cloud",
                                               "c0.app1.tenant1.gcp-us-south1-b.z.vespa-app.cloud",
                                               "c0.app1.tenant1.aws-us-east-1.w.vespa-app.cloud",
                                               "c0.app1.tenant1.gcp-us-south1.w.vespa-app.cloud",
                                               "r0.app1.tenant1.g.vespa-app.cloud");
        assertEquals(Set.copyOf(expectedRecords), tester.recordNames());

        assertEquals(List.of("lb-0--tenant1.app1.default--prod.aws-us-east-1c."), tester.recordDataOf(Record.Type.CNAME, expectedRecords.get(0)));
        assertEquals(List.of("10.0.0.0"), tester.recordDataOf(Record.Type.A, expectedRecords.get(1)));
        assertEquals(List.of("weighted/10.0.0.0/prod.gcp-us-south1-b/1"), tester.recordDataOf(Record.Type.DIRECT, expectedRecords.get(3)));
        assertEquals(List.of("latency/c0.app1.tenant1.aws-us-east-1.w.vespa-app.cloud/dns-zone-1/prod.aws-us-east-1c",
                             "latency/c0.app1.tenant1.gcp-us-south1.w.vespa-app.cloud/ignored/prod.gcp-us-south1-b"),
                     tester.recordDataOf(Record.Type.ALIAS, expectedRecords.get(4)));
    }

    @Test
    void global_routing_policies_in_public() {
        var tester = new RoutingPoliciesTester(SystemName.Public);
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        List<ZoneId> prodZones = tester.controllerTester().controller().zoneRegistry().zones().all().in(Environment.prod).ids();
        ZoneId zone1 = prodZones.get(0);
        ZoneId zone2 = prodZones.get(1);

        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("default", "default")
                .trustDefaultCertificate()
                .build();
        context.submit(applicationPackage).deploy();

        tester.assertTargets(context.instanceId(), EndpointId.defaultId(),
                             ClusterSpec.Id.from("default"), 0,
                             Map.of(zone1, 1L, zone2, 1L));
        assertEquals(Set.of("app1.tenant1.aws-eu-west-1.w.vespa-app.cloud",
                            "app1.tenant1.aws-eu-west-1a.z.vespa-app.cloud",
                            "app1.tenant1.aws-us-east-1.w.vespa-app.cloud",
                            "app1.tenant1.aws-us-east-1c.z.vespa-app.cloud",
                            "app1.tenant1.g.vespa-app.cloud"),
                     tester.recordNames(),
                     "Registers expected DNS names");
    }

    @Test
    void manual_deployment_creates_routing_policy() {
        // Empty application package is valid in manually deployed environments
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        var emptyApplicationPackage = new ApplicationPackageBuilder().build();
        var zone = ZoneId.from("dev", "us-east-1");
        var zoneApi = ZoneApiMock.from(zone.environment(), zone.region());
        tester.controllerTester().serviceRegistry().zoneRegistry()
              .setZones(zoneApi)
              .exclusiveRoutingIn(zoneApi);

        // Deploy to dev
        context.runJob(zone, emptyApplicationPackage);
        assertEquals(DeploymentSpec.empty, context.application().deploymentSpec(), "DeploymentSpec is not persisted");
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(context.instanceId()).size());
        assertEquals(Set.of("app1.tenant1.us-east-1.dev.vespa.oath.cloud"), tester.recordNames());
    }

    @Test
    void manual_deployment_creates_routing_policy_with_non_empty_spec() {
        // Initial deployment
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        context.submit(applicationPackage).deploy();
        var zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().setRoutingMethod(List.of(zone), RoutingMethod.exclusive);
        var prodRecords = Set.of("app1.tenant1.us-central-1.vespa.oath.cloud", "app1.tenant1.us-west-1.vespa.oath.cloud");
        assertEquals(prodRecords, tester.recordNames());

        // Deploy to dev under different instance
        var devContext = tester.newDeploymentContext(context.application().id().instance("user"));
        devContext.runJob(zone, applicationPackage);

        assertEquals(applicationPackage.deploymentSpec(), context.application().deploymentSpec(), "DeploymentSpec is persisted");
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(devContext.instanceId()).size());
        assertEquals(Sets.union(prodRecords, Set.of("user.app1.tenant1.us-east-1.dev.vespa.oath.cloud")), tester.recordNames());
    }

    @Test
    void reprovisioning_load_balancer_preserves_cname_record() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");

        // Initial load balancer is provisioned
        tester.provisionLoadBalancers(1, context.instanceId(), zone1);
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .build();

        // Application is deployed
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        var expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(1, tester.policiesOf(context.instanceId()).size());

        // Application is removed and the load balancer is deprovisioned
        tester.controllerTester().controller().applications().deactivate(context.instanceId(), zone1);
        tester.controllerTester().configServer().removeLoadBalancers(context.instanceId(), zone1);

        // Load balancer for the same application is provisioned again, but with a different hostname
        var newHostname = HostName.of("new-hostname");
        var loadBalancer = new LoadBalancer("LB-0-Z-" + zone1.value(),
                                            context.instanceId(),
                                            ClusterSpec.Id.from("c0"),
                                            Optional.of(newHostname),
                                            Optional.empty(),
                                            LoadBalancer.State.active,
                                            Optional.of("dns-zone-1"),
                                            Optional.empty(),
                                            Optional.empty(),
                                            true);
        tester.controllerTester().configServer().putLoadBalancers(zone1, List.of(loadBalancer));

        // Application redeployment preserves DNS record
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(1, tester.policiesOf(context.instanceId()).size());
        assertEquals(newHostname.value() + ".",
                tester.recordDataOf(Record.Type.CNAME, expectedRecords.iterator().next()).get(0),
                "CNAME points to current load balancer");
    }

    @Test
    @Timeout(30)
    void private_dns_for_vpc_endpoint() {
        // Challenge answered for endpoint
        RoutingPoliciesTester tester = new RoutingPoliciesTester();
        tester.tester.controllerTester().serviceRegistry().vpcEndpointService().enabled.set(true);

        DeploymentContext app = tester.newDeploymentContext("t", "a", "default");
        ApplicationPackage appPackage = applicationPackageBuilder().region(zone3.region()).build();
        app.submit(appPackage);

        app.deploy();

        // TXT records are cleaned up as we go—the last challenge is the last to go here, and we must flush it ourselves.
        assertEquals(Set.of("a.t.aws-us-east-1a.vespa.oath.cloud",
                            "challenge--a.t.aws-us-east-1a.vespa.oath.cloud"),
                     tester.recordNames());
        app.flushDnsUpdates();
        assertEquals(Set.of(new Record(Type.CNAME,
                                       RecordName.from("a.t.aws-us-east-1a.vespa.oath.cloud"),
                                       RecordData.from("lb-0--t.a.default--prod.aws-us-east-1a."))),
                     tester.controllerTester().nameService().records());


        tester.tester.controllerTester().serviceRegistry().vpcEndpointService().outcomes
                .put(RecordName.from("challenge--a.t.aws-us-east-1a.vespa.oath.cloud"), State.running);

        // Deployment fails because challenge is not answered (immediately).
        assertEquals("Status of run 2 of production-aws-us-east-1a for t.a ==> expected: <succeeded> but was: <unfinished>",
                     assertThrows(AssertionError.class,
                                  () -> app.submit(appPackage).deploy())
                             .getMessage());
    }

    @Test
    void set_global_endpoint_status() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");

        // Provision load balancers and deploy application
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0", zone1.region().value(), zone2.region().value())
                .endpoint("r1", "c0", zone1.region().value(), zone2.region().value())
                .build();
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        // Global DNS record is created
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone1, zone2);

        // Global routing status is overridden in one zone
        var changedAt = tester.controllerTester().clock().instant();
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone1), RoutingStatus.Value.out,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();

        // Inactive zone is removed from global DNS record
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone2);

        // Status details is stored in policy
        var policy1 = tester.routingPolicies().read(context.deploymentIdIn(zone1)).first().get();
        assertEquals(RoutingStatus.Value.out, policy1.status().routingStatus().value());
        assertEquals(RoutingStatus.Agent.tenant, policy1.status().routingStatus().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), policy1.status().routingStatus().changedAt());

        // Other zone remains in
        var policy2 = tester.routingPolicies().read(context.deploymentIdIn(zone2)).first().get();
        assertEquals(RoutingStatus.Value.in, policy2.status().routingStatus().value());
        assertEquals(RoutingStatus.Agent.system, policy2.status().routingStatus().agent());
        assertEquals(Instant.EPOCH, policy2.status().routingStatus().changedAt());

        // Next deployment does not affect status
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone2);

        // Deployment is set back in
        tester.controllerTester().clock().advance(Duration.ofHours(1));
        changedAt = tester.controllerTester().clock().instant();
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone1), RoutingStatus.Value.in, RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone1, zone2);

        policy1 = tester.routingPolicies().read(context.deploymentIdIn(zone1)).first().get();
        assertEquals(RoutingStatus.Value.in, policy1.status().routingStatus().value());
        assertEquals(RoutingStatus.Agent.tenant, policy1.status().routingStatus().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), policy1.status().routingStatus().changedAt());

        // Deployment is set out through a new deployment.xml
        var applicationPackage2 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region(), false)
                .endpoint("r0", "c0", zone1.region().value(), zone2.region().value())
                .endpoint("r1", "c0", zone1.region().value(), zone2.region().value())
                .build();
        context.submit(applicationPackage2).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone1);

        // ... back in
        var applicationPackage3 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0", zone1.region().value(), zone2.region().value())
                .endpoint("r1", "c0", zone1.region().value(), zone2.region().value())
                .build();
        context.submit(applicationPackage3).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone1, zone2);
    }

    @Test
    void set_zone_global_endpoint_status() {
        var tester = new RoutingPoliciesTester();
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var context2 = tester.newDeploymentContext("tenant2", "app2", "default");
        var contexts = List.of(context1, context2);

        // Deploy applications
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("default", "c0", zone1.region().value(), zone2.region().value())
                .build();
        for (var context : contexts) {
            tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);
            context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
            tester.assertTargets(context.instanceId(), EndpointId.defaultId(), 0, zone1, zone2);
        }

        // Set zone out
        tester.routingPolicies().setRoutingStatus(zone2, RoutingStatus.Value.out);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1);
        for (var context : contexts) {
            var policies = tester.routingPolicies().read(context.instanceId());
            assertTrue(policies.asList().stream()
                            .map(RoutingPolicy::status)
                            .map(RoutingPolicy.Status::routingStatus)
                            .map(RoutingStatus::value)
                            .allMatch(status -> status == RoutingStatus.Value.in),
                    "Global routing status for policy remains " + RoutingStatus.Value.in);
        }
        var changedAt = tester.controllerTester().clock().instant();
        var zonePolicy = tester.controllerTester().controller().curator().readZoneRoutingPolicy(zone2);
        assertEquals(RoutingStatus.Value.out, zonePolicy.routingStatus().value());
        assertEquals(RoutingStatus.Agent.operator, zonePolicy.routingStatus().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), zonePolicy.routingStatus().changedAt());

        // Setting status per deployment does not affect status as entire zone is out
        tester.routingPolicies().setRoutingStatus(context1.deploymentIdIn(zone2), RoutingStatus.Value.in, RoutingStatus.Agent.tenant);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1);

        // Set single deployment out
        tester.routingPolicies().setRoutingStatus(context1.deploymentIdIn(zone2), RoutingStatus.Value.out, RoutingStatus.Agent.tenant);
        context1.flushDnsUpdates();

        // Set zone back in. Deployment set explicitly out, remains out, the rest are in
        tester.routingPolicies().setRoutingStatus(zone2, RoutingStatus.Value.in);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1, zone2);
    }

    @Test
    void non_production_deployment_is_not_registered_in_global_endpoint() {
        var tester = new RoutingPoliciesTester(SystemName.Public);

        // Configure the system to use the same region for test, staging and prod
        var context = tester.tester.newDeploymentContext();
        var endpointId = EndpointId.of("r0");
        var applicationPackage = applicationPackageBuilder()
                .trustDefaultCertificate()
                .region("aws-us-east-1c")
                .endpoint(endpointId.id(), "default")
                .build();

        // Application starts deployment
        context = context.submit(applicationPackage);
        for (var testJob : List.of(DeploymentContext.systemTest, DeploymentContext.stagingTest)) {
            context = context.runJob(testJob);
            // Since runJob implicitly tears down the deployment and immediately deletes DNS records associated with the
            // deployment, we consume only one DNS update at a time here
            do {
                context.flushDnsUpdates(1);
                tester.assertTargets(context.instanceId(), endpointId, 0);
            } while (!tester.recordNames().isEmpty());
        }

        // Deployment completes
        context.completeRollout();
        tester.assertTargets(context.instanceId(), endpointId, ClusterSpec.Id.from("default"), 0, Map.of(ZoneId.from("prod", "aws-us-east-1c"), 1L));
    }

    @Test
    void changing_global_routing_status_never_removes_all_members() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");

        // Provision load balancers and deploy application
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);
        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0", zone1.region().value(), zone2.region().value())
                .build();
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        // Global DNS record is created, pointing to all configured zones
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);

        // Global routing status is overridden for one deployment
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone1), RoutingStatus.Value.out,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);

        // Setting other deployment out implicitly sets all deployments in. Weight is set to zero, but that has no
        // impact on routing decisions when the weight sum is zero
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone2), RoutingStatus.Value.out,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, ImmutableMap.of(zone1, 0L, zone2, 0L));

        // One inactive deployment is put back in. Global DNS record now points to the only active deployment
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone1), RoutingStatus.Value.in,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);

        // Setting zone (containing active deployment) out puts all deployments in
        tester.routingPolicies().setRoutingStatus(zone1, RoutingStatus.Value.out);
        context.flushDnsUpdates();
        assertEquals(RoutingStatus.Value.out, tester.routingPolicies().read(zone1).routingStatus().value());
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, ImmutableMap.of(zone1, 0L, zone2, 0L));

        // Setting zone back in removes the currently inactive deployment
        tester.routingPolicies().setRoutingStatus(zone1, RoutingStatus.Value.in);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);

        // Inactive deployment is set in
        tester.routingPolicies().setRoutingStatus(context.deploymentIdIn(zone2), RoutingStatus.Value.in,
                RoutingStatus.Agent.tenant);
        context.flushDnsUpdates();
        for (var policy : tester.routingPolicies().read(context.instanceId())) {
            assertSame(RoutingStatus.Value.in, policy.status().routingStatus().value());
        }
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
    }

    @Test
    void application_endpoint_routing_policy() {
        RoutingPoliciesTester tester = new RoutingPoliciesTester();
        TenantAndApplicationId application = TenantAndApplicationId.from("tenant1", "app1");
        ApplicationId betaInstance = application.instance("beta");
        ApplicationId mainInstance = application.instance("main");

        DeploymentContext betaContext = tester.newDeploymentContext(betaInstance);
        DeploymentContext mainContext = tester.newDeploymentContext(mainInstance);
        var applicationPackage = applicationPackageBuilder()
                .instances("beta,main")
                .region(zone5.region())
                .region(zone6.region())
                .applicationEndpoint("a0", "c0",
                                     Map.of(zone5.region().value(), Map.of(betaInstance.instance(), 2,
                                                                           mainInstance.instance(), 8),
                                            zone6.region().value(), Map.of(mainInstance.instance(), 7)))
                .applicationEndpoint("a1", "c1", zone6.region().value(),
                                     Map.of(betaInstance.instance(), 4,
                                            mainInstance.instance(), 6))
                .build();
        for (var zone : List.of(zone5, zone6)) {
            tester.provisionLoadBalancers(2, betaInstance, zone);
            tester.provisionLoadBalancers(2, mainInstance, zone);
        }

        // Deploy both instances
        betaContext.submit(applicationPackage).deploy();

        // Application endpoint points to both instances with correct weights
        DeploymentId betaZone5 = betaContext.deploymentIdIn(zone5);
        DeploymentId mainZone5 = mainContext.deploymentIdIn(zone5);
        DeploymentId betaZone6 = betaContext.deploymentIdIn(zone6);
        DeploymentId mainZone6 = mainContext.deploymentIdIn(zone6);
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone5, 2,
                                    mainZone5, 8,
                                    mainZone6, 7));
        tester.assertTargets(application, EndpointId.of("a1"), ClusterSpec.Id.from("c1"), 1,
                             Map.of(betaZone6, 4,
                                    mainZone6, 6));

        // Weights are updated
        applicationPackage = applicationPackageBuilder()
                .instances("beta,main")
                .region(zone5.region())
                .region(zone6.region())
                .applicationEndpoint("a0", "c0",
                                     Map.of(zone5.region().value(), Map.of(betaInstance.instance(), 3,
                                                                           mainInstance.instance(), 7),
                                            zone6.region().value(), Map.of(mainInstance.instance(), 2)))
                .applicationEndpoint("a1", "c1", zone6.region().value(),
                                     Map.of(betaInstance.instance(), 1,
                                            mainInstance.instance(), 9))
                .build();
        betaContext.submit(applicationPackage).deploy();
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone5, 3,
                                    mainZone5, 7,
                                    mainZone6, 2));
        tester.assertTargets(application, EndpointId.of("a1"), ClusterSpec.Id.from("c1"), 1,
                             Map.of(betaZone6, 1,
                                    mainZone6, 9));

        // An endpoint is removed
        applicationPackage = applicationPackageBuilder()
                .instances("beta,main")
                .region(zone5.region())
                .region(zone6.region())
                .applicationEndpoint("a0", "c0", zone5.region().value(),
                                     Map.of(betaInstance.instance(), 1))
                .build();
        betaContext.submit(applicationPackage).deploy();

        // Application endpoints now point to a single instance
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone5, 1));
        assertTrue(tester.controllerTester().controller().routing()
                        .readDeclaredEndpointsOf(application)
                        .named(EndpointId.of("a1")).isEmpty(),
                "Endpoint removed");
    }

    @Test
    void application_endpoint_routing_status() {
        RoutingPoliciesTester tester = new RoutingPoliciesTester();
        TenantAndApplicationId application = TenantAndApplicationId.from("tenant1", "app1");
        ApplicationId betaInstance = application.instance("beta");
        ApplicationId mainInstance = application.instance("main");

        DeploymentContext betaContext = tester.newDeploymentContext(betaInstance);
        DeploymentContext mainContext = tester.newDeploymentContext(mainInstance);
        var applicationPackage = applicationPackageBuilder()
                .instances("beta,main")
                .region(zone5.region())
                .region(zone6.region())
                .applicationEndpoint("a0", "c0", Map.of(zone5.region().value(), Map.of(betaInstance.instance(), 2,
                                                                                       mainInstance.instance(), 8),
                                                        zone6.region().value(), Map.of(mainInstance.instance(), 9)))
                .build();
        tester.provisionLoadBalancers(1, betaInstance, zone5);
        tester.provisionLoadBalancers(1, mainInstance, zone5);
        tester.provisionLoadBalancers(1, mainInstance, zone6);

        // Deploy both instances
        betaContext.submit(applicationPackage).deploy();

        // Application endpoint points to both instances with correct weights
        DeploymentId betaZone1 = betaContext.deploymentIdIn(zone5);
        DeploymentId mainZone1 = mainContext.deploymentIdIn(zone5);
        DeploymentId mainZone2 = mainContext.deploymentIdIn(zone6);
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone1, 2,
                                    mainZone1, 8,
                                    mainZone2, 9));

        // Changing routing status removes deployment from DNS
        tester.routingPolicies().setRoutingStatus(mainZone1, RoutingStatus.Value.out, RoutingStatus.Agent.tenant);
        betaContext.flushDnsUpdates();
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone1, 2,
                                    mainZone2, 9));

        // Changing routing status for remaining deployments adds back all deployments, because removing all deployments
        // puts all IN
        tester.routingPolicies().setRoutingStatus(betaZone1, RoutingStatus.Value.out, RoutingStatus.Agent.tenant);
        tester.routingPolicies().setRoutingStatus(mainZone2, RoutingStatus.Value.out, RoutingStatus.Agent.tenant);
        betaContext.flushDnsUpdates();
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(betaZone1, 2,
                                    mainZone1, 8,
                                    mainZone2, 9));

        // Activating main deployment allows us to deactivate the beta deployment
        tester.routingPolicies().setRoutingStatus(mainZone1, RoutingStatus.Value.in, RoutingStatus.Agent.tenant);
        betaContext.flushDnsUpdates();
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                             Map.of(mainZone1, 8));

        // Activate all deployments again
        tester.routingPolicies().setRoutingStatus(betaZone1, RoutingStatus.Value.in, RoutingStatus.Agent.tenant);
        tester.routingPolicies().setRoutingStatus(mainZone2, RoutingStatus.Value.in, RoutingStatus.Agent.tenant);
        betaContext.flushDnsUpdates();
        tester.assertTargets(application, EndpointId.of("a0"), ClusterSpec.Id.from("c0"), 0,
                Map.of(betaZone1, 2,
                       mainZone1, 8,
                       mainZone2, 9));
    }

    /** Returns an application package builder that satisfies requirements for a directly routed endpoint */
    private static ApplicationPackageBuilder applicationPackageBuilder() {
        return new ApplicationPackageBuilder().athenzIdentity(AthenzDomain.from("domain"),
                                                              AthenzService.from("service"));
    }

    private static List<LoadBalancer> createLoadBalancers(ZoneId zone, ApplicationId application, boolean shared, int count) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<DomainName> lbHostname;
            Optional<String> ipAddress;
            if (zone.region().value().startsWith("gcp-")) {
                lbHostname = Optional.empty();
                ipAddress = Optional.of("10.0.0." + i);
            } else {
                String hostname = shared ? "shared-lb--" + zone.value() : "lb-" + i + "--" + application.toFullString() + "--" + zone.value();
                lbHostname = Optional.of(DomainName.of(hostname));
                ipAddress = Optional.empty();
            }
            loadBalancers.add(
                    new LoadBalancer("LB-" + i + "-Z-" + zone.value(),
                                     application,
                                     ClusterSpec.Id.from("c" + i),
                                     lbHostname,
                                     ipAddress,
                                     LoadBalancer.State.active,
                                     Optional.of("dns-zone-1").filter(__ -> lbHostname.isPresent()),
                                     Optional.empty(),
                                     Optional.empty(),
                                     true));
        }
        return loadBalancers;
    }

    private static List<ZoneId> publicZones() {
        var sharedRegion = RegionName.from("aws-us-east-1c");
        return List.of(ZoneId.from(Environment.prod, sharedRegion),
                       ZoneId.from(Environment.prod, RegionName.from("aws-eu-west-1a")),
                       ZoneId.from(Environment.prod, RegionName.from("gcp-us-south1-b")),
                       ZoneId.from(Environment.staging, RegionName.from("us-east-3")),
                       ZoneId.from(Environment.test, RegionName.from("us-east-1")));
    }

    private static class RoutingPoliciesTester {

        private final DeploymentTester tester;

        public RoutingPoliciesTester() {
            this(SystemName.main);
        }

        public RoutingPoliciesTester(SystemName system) {
            this(new DeploymentTester(system.isPublic() ? new ControllerTester(new RotationsConfig.Builder().build(), system)
                                                        : new ControllerTester(system)),
                 true);
        }

        public RoutingPoliciesTester(DeploymentTester tester, boolean exclusiveRouting) {
            this.tester = tester;
            List<ZoneId> zones;
            if (tester.controller().system().isPublic()) {
                zones = publicZones();
                tester.controllerTester().setZones(zones);
            } else {
                zones = new ArrayList<>(tester.controllerTester().zoneRegistry().zones().all().ids()); // Default zones
                zones.add(zone4); // Missing from default ZoneRegistryMock zones
                tester.controllerTester().setZones(zones);
                tester.controllerTester().zoneRegistry().addZones(ZoneApiMock.newBuilder().withId(zone5.value()).withCloud("aws").build());
                tester.controllerTester().zoneRegistry().addZones(ZoneApiMock.newBuilder().withId(zone6.value()).withCloud("gcp").build());
                zones.add(zone5);
                zones.add(zone6);
                tester.configServer().bootstrap(zones, SystemApplication.notController());
            }
            if (exclusiveRouting) {
                tester.controllerTester().setRoutingMethod(zones, RoutingMethod.exclusive);
            }
        }

        public RoutingPolicies routingPolicies() {
            return tester.controllerTester().controller().routing().policies();
        }

        public DeploymentContext newDeploymentContext(String tenant, String application, String instance) {
            return tester.newDeploymentContext(tenant, application, instance);
        }

        public DeploymentContext newDeploymentContext(ApplicationId instance) {
            return tester.newDeploymentContext(instance);
        }

        public ControllerTester controllerTester() {
            return tester.controllerTester();
        }

        private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, boolean shared, ZoneId... zones) {
            for (ZoneId zone : zones) {
                tester.configServer().removeLoadBalancers(application, zone);
                tester.configServer().putLoadBalancers(zone, createLoadBalancers(zone, application, shared, clustersPerZone));
            }
        }

        private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, ZoneId... zones) {
            provisionLoadBalancers(clustersPerZone, application, false, zones);
        }

        private RoutingPolicyList policiesOf(ApplicationId instance) {
            return tester.controller().routing().policies().read(instance);
        }

        private Set<String> recordNames() {
            return tester.controllerTester().nameService().records().stream()
                         .map(Record::name)
                         .map(RecordName::asString)
                         .collect(Collectors.toSet());
        }

        private Set<String> aliasDataOf(String name) {
            return tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from(name)).stream()
                         .map(Record::data)
                         .map(RecordData::asString)
                         .collect(Collectors.toSet());
        }

        private List<String> recordDataOf(Record.Type type, String name) {
            return tester.controllerTester().nameService().findRecords(type, RecordName.from(name)).stream()
                         .map(Record::data)
                         .map(RecordData::asString)
                         .toList();
        }

        /** Assert that an application endpoint points to given targets and weights */
        private void assertTargets(TenantAndApplicationId application, EndpointId endpointId, ClusterSpec.Id cluster,
                                   int loadBalancerId, Map<DeploymentId, Integer> deploymentWeights) {
            Map<String, List<DeploymentId>> deploymentsByDnsName = new HashMap<>();
            for (var deployment : deploymentWeights.keySet()) {
                EndpointList applicationEndpoints = tester.controller().routing().readDeclaredEndpointsOf(application)
                                                          .named(endpointId)
                                                          .targets(deployment)
                                                          .cluster(cluster);
                assertEquals(1,
                             applicationEndpoints.size(),
                             "Expected a single endpoint with ID '" + endpointId + "'");
                String dnsName = applicationEndpoints.asList().get(0).dnsName();
                deploymentsByDnsName.computeIfAbsent(dnsName, (k) -> new ArrayList<>())
                                    .add(deployment);
            }
            assertTrue(deploymentsByDnsName.size() >= 1, "Found " + endpointId + " for " + application);
            deploymentsByDnsName.forEach((dnsName, deployments) -> {
                Set<String> weightedTargets = deployments.stream()
                                                         .map(d -> "weighted/lb-" + loadBalancerId + "--" +
                                                                   d.applicationId().toFullString() + "--" + d.zoneId().value() +
                                                                   "/dns-zone-1/" + d.zoneId().value() + "/" + deploymentWeights.get(d))
                                                         .collect(Collectors.toSet());
                assertEquals(weightedTargets, aliasDataOf(dnsName), dnsName + " has expected targets");
            });
        }

        /** Assert that an instance endpoint points to given targets and weights */
        private void assertTargets(ApplicationId instance, EndpointId endpointId, ClusterSpec.Id cluster,
                                   int loadBalancerId, Map<ZoneId, Long> zoneWeights) {
            Set<String> latencyTargets = new HashSet<>();
            Map<String, List<ZoneId>> zonesByRegionEndpoint = new HashMap<>();
            for (var zone : zoneWeights.keySet()) {
                DeploymentId deployment = new DeploymentId(instance, zone);
                EndpointList regionEndpoints = tester.controller().routing().readEndpointsOf(deployment)
                                                     .cluster(cluster)
                                                     .scope(Endpoint.Scope.weighted);
                Endpoint regionEndpoint = regionEndpoints.first().orElseThrow(() -> new IllegalArgumentException("No region endpoint found for " + cluster + " in " + deployment));
                zonesByRegionEndpoint.computeIfAbsent(regionEndpoint.dnsName(), (k) -> new ArrayList<>())
                                     .add(zone);
            }
            zonesByRegionEndpoint.forEach((regionEndpoint, zonesInRegion) -> {
                Set<String> weightedTargets = zonesInRegion.stream()
                                                           .map(z -> "weighted/lb-" + loadBalancerId + "--" +
                                                                     instance.toFullString() + "--" + z.value() +
                                                                     "/dns-zone-1/" + z.value() + "/" + zoneWeights.get(z))
                                                           .collect(Collectors.toSet());
                assertEquals(weightedTargets,
                             aliasDataOf(regionEndpoint),
                             "Region endpoint " + regionEndpoint + " points to load balancer");
                ZoneId zone = zonesInRegion.get(0);
                String latencyTarget = "latency/" + regionEndpoint + "/dns-zone-1/" + zone.value();
                latencyTargets.add(latencyTarget);
            });
            List<DeploymentId> deployments = zoneWeights.keySet().stream().map(z -> new DeploymentId(instance, z)).toList();
            String globalEndpoint = tester.controller().routing().readDeclaredEndpointsOf(instance)
                                          .named(endpointId)
                                          .targets(deployments)
                                          .primary()
                                          .map(Endpoint::dnsName)
                                          .orElse("<none>");
            assertEquals(latencyTargets, Set.copyOf(aliasDataOf(globalEndpoint)), "Global endpoint " + globalEndpoint + " points to expected latency targets");

        }

        private void assertTargets(ApplicationId application, EndpointId endpointId, int loadBalancerId, ZoneId... zones) {
            Map<ZoneId, Long> zoneWeights = new LinkedHashMap<>();
            for (var zone : zones) {
                zoneWeights.put(zone, 1L);
            }
            assertTargets(application, endpointId, ClusterSpec.Id.from("c" + loadBalancerId), loadBalancerId, zoneWeights);
        }

        private void assertTargets(ApplicationId application, EndpointId endpointId, int loadBalancerId, Map<ZoneId, Long> zoneWeights) {
            assertTargets(application, endpointId, ClusterSpec.Id.from("c" + loadBalancerId), loadBalancerId, zoneWeights);
        }

    }

}
