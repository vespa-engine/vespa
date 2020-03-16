// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.google.common.collect.Sets;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.RoutingController;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author mortent
 * @author mpolden
 */
public class RoutingPoliciesTest {

    private final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private final ZoneId zone3 = ZoneId.from("prod", "us-east-3");

    private final ApplicationPackage applicationPackage = applicationPackageBuilder().region(zone1.region())
                                                                                     .region(zone2.region())
                                                                                     .build();

    @Test
    public void global_routing_policies() {
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
        assertEquals("Routing policy count is equal to cluster count",
                     numberOfDeployments * clustersPerZone,
                     tester.policiesOf(context1.instance().id()).size());

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

        // All endpoints for app1 are removed
        ApplicationPackage applicationPackage4 = applicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context1.submit(applicationPackage4).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        tester.assertTargets(context1.instanceId(), EndpointId.of("r0"), 0);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r1"), 0);
        tester.assertTargets(context1.instanceId(), EndpointId.of("r2"), 0);
        var policies = tester.policiesOf(context1.instanceId());
        assertEquals(clustersPerZone * numberOfDeployments, policies.size());
        assertTrue("Rotation membership is removed from all policies",
                   policies.stream().allMatch(policy -> policy.endpoints().isEmpty()));
        assertEquals("Rotations for " + context2.application() + " are not removed", 2, tester.aliasDataOf(endpoint4).size());
    }

    @Test
    public void zone_routing_policies() {
        var tester = new RoutingPoliciesTester();
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");

        // Deploy application
        int clustersPerZone = 2;
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);
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
        tester.provisionLoadBalancers(clustersPerZone + 1, context1.instanceId(), zone1, zone2);
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
        tester.provisionLoadBalancers(clustersPerZone, context2.instanceId(), zone1, zone2);
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
        assertEquals(expectedRecords.stream().sorted().collect(Collectors.toList()), tester.recordNames().stream().sorted().collect(Collectors.toList()));
        assertEquals(4, tester.policiesOf(context2.instanceId()).size());

        // Deploy removes cluster from app1
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);
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
        assertTrue("Removes stale routing policies " + context2.application(), tester.routingPolicies().get(context2.instanceId()).isEmpty());
        assertEquals("Keeps routing policies for " + context1.application(), 4, tester.routingPolicies().get(context1.instanceId()).size());
    }

    @Test
    public void global_routing_policies_in_rotationless_system() {
        var tester = new RoutingPoliciesTester(new DeploymentTester(new ControllerTester(new RotationsConfig.Builder().build())));
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);

        var applicationPackage = applicationPackageBuilder()
                .region(zone1.region().value())
                .endpoint("r0", "c0")
                .build();
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();

        var endpoint = "r0.app1.tenant1.global.vespa.oath.cloud";
        assertEquals(endpoint + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint));
        assertTrue("No rotations assigned", context.application().instances().values().stream()
                                                   .map(Instance::rotations)
                                                   .allMatch(List::isEmpty));
    }

    @Test
    public void manual_deployment_creates_routing_policy() {
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
        tester.controllerTester().controller().applications().deploy(context.instanceId(), zone, Optional.of(emptyApplicationPackage), DeployOptions.none());
        assertEquals("DeploymentSpec is not persisted", DeploymentSpec.empty, context.application().deploymentSpec());
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(context.instanceId()).size());
        assertEquals(Set.of("app1.tenant1.us-east-1.dev.vespa.oath.cloud"), tester.recordNames());
    }

    @Test
    public void manual_deployment_creates_routing_policy_with_non_empty_spec() {
        // Initial deployment
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        context.submit(applicationPackage).deploy();
        var zone = ZoneId.from("dev", "us-east-1");
        var zoneApi = ZoneApiMock.from(zone.environment(), zone.region());
        tester.controllerTester().serviceRegistry().zoneRegistry()
              .setZones(zoneApi)
              .exclusiveRoutingIn(zoneApi);
        var prodRecords = Set.of("app1.tenant1.us-central-1.vespa.oath.cloud", "app1.tenant1.us-west-1.vespa.oath.cloud");
        assertEquals(prodRecords, tester.recordNames());

        // Deploy to dev under different instance
        var devInstance = context.application().id().instance("user");
        tester.controllerTester().controller().applications().deploy(devInstance, zone, Optional.of(applicationPackage), DeployOptions.none());
        assertEquals("DeploymentSpec is persisted", applicationPackage.deploymentSpec(), context.application().deploymentSpec());
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(devInstance).size());
        assertEquals(Sets.union(prodRecords, Set.of("user.app1.tenant1.us-east-1.dev.vespa.oath.cloud")), tester.recordNames());
    }

    @Test
    public void reprovisioning_load_balancer_preserves_cname_record() {
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
        var newHostname = HostName.from("new-hostname");
        var loadBalancer = new LoadBalancer("LB-0-Z-" + zone1.value(),
                                            context.instanceId(),
                                            ClusterSpec.Id.from("c0"),
                                            newHostname,
                                            LoadBalancer.State.active,
                                            Optional.of("dns-zone-1"));
        tester.controllerTester().configServer().putLoadBalancers(zone1, List.of(loadBalancer));

        // Application redeployment preserves DNS record
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(1, tester.policiesOf(context.instanceId()).size());
        assertEquals("CNAME points to current load blancer", newHostname.value() + ".",
                     tester.cnameDataOf(expectedRecords.iterator().next()).get(0));
    }

    @Test
    public void set_global_endpoint_status() {
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
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone1), GlobalRouting.Status.out,
                                                        GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();

        // Inactive zone is removed from global DNS record
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone2);

        // Status details is stored in policy
        var policy1 = tester.routingPolicies().get(context.deploymentIdIn(zone1)).values().iterator().next();
        assertEquals(GlobalRouting.Status.out, policy1.status().globalRouting().status());
        assertEquals(GlobalRouting.Agent.tenant, policy1.status().globalRouting().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), policy1.status().globalRouting().changedAt());

        // Other zone remains in
        var policy2 = tester.routingPolicies().get(context.deploymentIdIn(zone2)).values().iterator().next();
        assertEquals(GlobalRouting.Status.in, policy2.status().globalRouting().status());
        assertEquals(GlobalRouting.Agent.system, policy2.status().globalRouting().agent());
        assertEquals(Instant.EPOCH, policy2.status().globalRouting().changedAt());

        // Next deployment does not affect status
        context.submit(applicationPackage).deferLoadBalancerProvisioningIn(Environment.prod).deploy();
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone2);

        // Deployment is set back in
        tester.controllerTester().clock().advance(Duration.ofHours(1));
        changedAt = tester.controllerTester().clock().instant();
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone1), GlobalRouting.Status.in, GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
        tester.assertTargets(context.instanceId(), EndpointId.of("r1"), 0, zone1, zone2);

        policy1 = tester.routingPolicies().get(context.deploymentIdIn(zone1)).values().iterator().next();
        assertEquals(GlobalRouting.Status.in, policy1.status().globalRouting().status());
        assertEquals(GlobalRouting.Agent.tenant, policy1.status().globalRouting().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), policy1.status().globalRouting().changedAt());

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
    public void set_zone_global_endpoint_status() {
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
        tester.routingPolicies().setGlobalRoutingStatus(zone2, GlobalRouting.Status.out);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1);
        for (var context : contexts) {
            var policies = tester.routingPolicies().get(context.instanceId());
            assertTrue("Global routing status for policy remains " + GlobalRouting.Status.in,
                       policies.values().stream()
                               .map(RoutingPolicy::status)
                               .map(Status::globalRouting)
                               .map(GlobalRouting::status)
                               .allMatch(status -> status == GlobalRouting.Status.in));
        }
        var changedAt = tester.controllerTester().clock().instant();
        var zonePolicy = tester.controllerTester().controller().curator().readZoneRoutingPolicy(zone2);
        assertEquals(GlobalRouting.Status.out, zonePolicy.globalRouting().status());
        assertEquals(GlobalRouting.Agent.operator, zonePolicy.globalRouting().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), zonePolicy.globalRouting().changedAt());

        // Setting status per deployment does not affect status as entire zone is out
        tester.routingPolicies().setGlobalRoutingStatus(context1.deploymentIdIn(zone2), GlobalRouting.Status.in, GlobalRouting.Agent.tenant);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1);

        // Set single deployment out
        tester.routingPolicies().setGlobalRoutingStatus(context1.deploymentIdIn(zone2), GlobalRouting.Status.out, GlobalRouting.Agent.tenant);
        context1.flushDnsUpdates();

        // Set zone back in. Deployment set explicitly out, remains out, the rest are in
        tester.routingPolicies().setGlobalRoutingStatus(zone2, GlobalRouting.Status.in);
        context1.flushDnsUpdates();
        tester.assertTargets(context1.instanceId(), EndpointId.defaultId(), 0, zone1);
        tester.assertTargets(context2.instanceId(), EndpointId.defaultId(), 0, zone1, zone2);
    }

    @Test
    public void non_production_deployment_is_not_registered_in_global_endpoint() {
        var tester = new RoutingPoliciesTester(SystemName.Public);

        // Configure the system to use the same region for test, staging and prod
        var sharedRegion = RegionName.from("aws-us-east-1c");
        var prodZone = ZoneId.from(Environment.prod, sharedRegion);
        var stagingZone = ZoneId.from(Environment.staging, sharedRegion);
        var testZone = ZoneId.from(Environment.test, sharedRegion);
        var zones = List.of(ZoneApiMock.from(prodZone),
                            ZoneApiMock.from(stagingZone),
                            ZoneApiMock.from(testZone));
        tester.controllerTester().zoneRegistry()
              .setZones(zones)
              .setRoutingMethod(zones, RoutingMethod.exclusive);
        tester.controllerTester().configServer().bootstrap(List.of(prodZone, stagingZone, testZone),
                                                           SystemApplication.all());

        var context = tester.tester.newDeploymentContext();
        var endpointId = EndpointId.of("r0");
        var applicationPackage = applicationPackageBuilder()
                .trustDefaultCertificate()
                .region(sharedRegion)
                .endpoint(endpointId.id(), "default")
                .build();

        // Application starts deployment
        context = context.submit(applicationPackage);
        for (var testJob : List.of(JobType.systemTest, JobType.stagingTest)) {
            context = context.runJob(testJob);
            // Since runJob implicitly tears down the deployment and immediately deletes DNS records associated with the
            // deployment, we consume only one DNS update at a time here
            do {
                context = context.flushDnsUpdates(1);
                tester.assertTargets(context.instanceId(), endpointId, 0);
            } while (!tester.recordNames().isEmpty());
        }

        // Deployment completes
        context.completeRollout();
        tester.assertTargets(context.instanceId(), endpointId, 0, prodZone);
    }

    @Test
    public void changing_global_routing_status_never_removes_all_members() {
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
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone1), GlobalRouting.Status.out,
                                                        GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone2);

        // Setting other deployment out implicitly sets all deployments in
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone2), GlobalRouting.Status.out,
                                                        GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);

        // One inactive deployment is put back in. Global DNS record now points to the only active deployment
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone1), GlobalRouting.Status.in,
                                                        GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);

        // Setting zone (containing active deployment) out puts all deployments in
        tester.routingPolicies().setGlobalRoutingStatus(zone1, GlobalRouting.Status.out);
        context.flushDnsUpdates();
        assertEquals(GlobalRouting.Status.out, tester.routingPolicies().get(zone1).globalRouting().status());
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);

        // Setting zone back in removes the currently inactive deployment
        tester.routingPolicies().setGlobalRoutingStatus(zone1, GlobalRouting.Status.in);
        context.flushDnsUpdates();
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1);

        // Inactive deployment is set in
        tester.routingPolicies().setGlobalRoutingStatus(context.deploymentIdIn(zone2), GlobalRouting.Status.in,
                                                        GlobalRouting.Agent.tenant);
        context.flushDnsUpdates();
        for (var policy : tester.routingPolicies().get(context.instanceId()).values()) {
            assertSame(GlobalRouting.Status.in, policy.status().globalRouting().status());
        }
        tester.assertTargets(context.instanceId(), EndpointId.of("r0"), 0, zone1, zone2);
    }

    /** Returns an application package builder that satisfies requirements for a directly routed endpoint */
    private static ApplicationPackageBuilder applicationPackageBuilder() {
        return new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .compileVersion(RoutingController.DIRECT_ROUTING_MIN_VERSION);
    }
    
    private static List<LoadBalancer> createLoadBalancers(ZoneId zone, ApplicationId application, int count) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            loadBalancers.add(
                    new LoadBalancer("LB-" + i + "-Z-" + zone.value(),
                                     application,
                                     ClusterSpec.Id.from("c" + i),
                                     HostName.from("lb-" + i + "--" + application.serializedForm() +
                                                   "--" + zone.value()),
                                     LoadBalancer.State.active,
                                     Optional.of("dns-zone-1")));
        }
        return loadBalancers;
    }

    private static class RoutingPoliciesTester {

        private final DeploymentTester tester;

        public RoutingPoliciesTester() {
            this(SystemName.main);
        }

        public RoutingPoliciesTester(SystemName system) {
            this(new DeploymentTester(new ControllerTester(new ServiceRegistryMock(system))));
        }

        public RoutingPolicies routingPolicies() {
            return tester.controllerTester().controller().routing().policies();
        }

        public DeploymentContext newDeploymentContext(String tenant, String application, String instance) {
            return tester.newDeploymentContext(tenant, application, instance);
        }

        public ControllerTester controllerTester() {
            return tester.controllerTester();
        }

        public RoutingPoliciesTester(DeploymentTester tester) {
            this.tester = tester;
            // Make all zones directly routed
            tester.controllerTester().zoneRegistry().exclusiveRoutingIn(tester.controllerTester().zoneRegistry().zones().all().zones());
        }

        private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, ZoneId... zones) {
            for (ZoneId zone : zones) {
                tester.configServer().removeLoadBalancers(application, zone);
                tester.configServer().putLoadBalancers(zone, createLoadBalancers(zone, application, clustersPerZone));
            }
        }

        private Collection<RoutingPolicy> policiesOf(ApplicationId instance) {
            return tester.controller().curator().readRoutingPolicies(instance).values();
        }

        private Set<String> recordNames() {
            return tester.controllerTester().nameService().records().stream()
                         .map(Record::name)
                         .map(RecordName::asString)
                         .collect(Collectors.toSet());
        }

        private List<String> aliasDataOf(String name) {
            return tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from(name)).stream()
                         .map(Record::data)
                         .map(RecordData::asString)
                         .collect(Collectors.toList());
        }

        private List<String> cnameDataOf(String name) {
            return tester.controllerTester().nameService().findRecords(Record.Type.CNAME, RecordName.from(name)).stream()
                         .map(Record::data)
                         .map(RecordData::asString)
                         .collect(Collectors.toList());
        }

        private void assertTargets(ApplicationId application, EndpointId endpointId, int loadBalancerId, ZoneId ...zone) {
            var endpoint = tester.controller().routing().endpointsOf(application)
                                 .named(endpointId)
                                 .targets(List.of(zone))
                                 .primary()
                                 .map(Endpoint::dnsName)
                                 .orElse("<none>");
            var zoneTargets = Arrays.stream(zone)
                                    .map(z -> "lb-" + loadBalancerId + "--" + application.serializedForm() + "--" +
                                              z.value() + "/dns-zone-1/" + z.value())
                                    .collect(Collectors.toSet());
            assertEquals("Global endpoint " + endpoint + " points to expected zones", zoneTargets,
                         Set.copyOf(aliasDataOf(endpoint)));
        }

    }

}
