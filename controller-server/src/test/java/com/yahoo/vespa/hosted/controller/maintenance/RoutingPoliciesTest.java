// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mortent
 * @author mpolden
 */
public class RoutingPoliciesTest {

    private final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private final ZoneId zone3 = ZoneId.from("prod", "us-east-3");

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .region(zone1.region())
            .region(zone2.region())
            .build();

    @Test
    public void global_routing_policies() {
        var tester = new RoutingPoliciesTester();
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default");
        int clustersPerZone = 2;
        int numberOfDeployments = 2;
        var applicationPackage = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);

        // Creates alias records
        context1.submit(applicationPackage).deploy();
        var endpoint1 = "r0.app1.tenant1.global.vespa.oath.cloud";
        var endpoint2 = "r1.app1.tenant1.global.vespa.oath.cloud";
        var endpoint3 = "r2.app1.tenant1.global.vespa.oath.cloud";

        assertEquals(endpoint1 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint1));
        assertEquals(endpoint2 + " points to c0 us-west-1",
                     List.of("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint2));
        assertEquals(endpoint3 + " points to c1 in all regions",
                     List.of("lb-1--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-1--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint3));
        assertEquals("Routing policy count is equal to cluster count",
                     numberOfDeployments * clustersPerZone,
                     tester.policiesOf(context1.instance().id()).size());

        // Applications gains a new deployment
        ApplicationPackage applicationPackage2 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        numberOfDeployments++;
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone3);
        context1.submit(applicationPackage2).deploy();

        // Endpoint is updated to contain cluster in new deployment
        assertEquals(endpoint1 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app1:default--prod.us-east-3/dns-zone-1/prod.us-east-3",
                             "lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint1));

        // Another application is deployed with a single cluster and global endpoint
        var endpoint4 = "r0.app2.tenant1.global.vespa.oath.cloud";
        tester.provisionLoadBalancers(1, context2.instanceId(), zone1, zone2);
        var applicationPackage3 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .build();
        context2.submit(applicationPackage3).deploy();
        assertEquals(endpoint4 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app2:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app2:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint4));

        // All endpoints for app1 are removed
        ApplicationPackage applicationPackage4 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context1.submit(applicationPackage4).deploy();
        assertEquals("DNS records are removed", List.of(), tester.aliasDataOf(endpoint1));
        assertEquals("DNS records are removed", List.of(), tester.aliasDataOf(endpoint2));
        assertEquals("DNS records are removed", List.of(), tester.aliasDataOf(endpoint3));
        Set<RoutingPolicy> policies = tester.policiesOf(context1.instanceId());
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
        context1.submit(applicationPackage).deploy();

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
        context1.submit(applicationPackage).deploy();
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(4, tester.policiesOf(context1.instanceId()).size());

        // Add 1 cluster in each zone and deploy
        tester.provisionLoadBalancers(clustersPerZone + 1, context1.instanceId(), zone1, zone2);
        context1.submit(applicationPackage).deploy();
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
        context2.submit(applicationPackage).deploy();
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
        assertEquals(expectedRecords, tester.recordNames());
        assertEquals(4, tester.policiesOf(context2.instanceId()).size());

        // Deploy removes cluster from app1
        tester.provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);
        context1.submit(applicationPackage).deploy();
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
        assertTrue("Removes stale routing policies " + context2.application(), tester.controllerTester().controller().applications().routingPolicies().get(context2.instanceId()).isEmpty());
        assertEquals("Keeps routing policies for " + context1.application(), 4, tester.controllerTester().controller().applications().routingPolicies().get(context1.instanceId()).size());
    }

    @Test
    public void global_routing_policies_in_rotationless_system() {
        var tester = new RoutingPoliciesTester(new DeploymentTester(new ControllerTester(new RotationsConfig.Builder().build())));
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        tester.provisionLoadBalancers(1, context.instanceId(), zone1, zone2);

        var applicationPackage = new ApplicationPackageBuilder()
                .region(zone1.region().value())
                .endpoint("r0", "c0")
                .build();
        context.submit(applicationPackage).deploy();

        var endpoint = "r0.app1.tenant1.global.vespa.oath.cloud";
        assertEquals(endpoint + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     tester.aliasDataOf(endpoint));
        assertTrue("No rotations assigned", context.application().instances().values().stream()
                                                   .map(Instance::rotations)
                                                   .allMatch(List::isEmpty));
    }

    @Test
    public void cluster_endpoints_resolve_from_policies() {
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        tester.provisionLoadBalancers(3, context.instanceId(), zone1);
        context.submit(applicationPackage).deploy();
        tester.controllerTester().serviceRegistry().routingGeneratorMock().putEndpoints(context.deploymentIdIn(zone1), Collections.emptyList());
        assertEquals(Map.of(ClusterSpec.Id.from("c0"),
                            URI.create("https://c0.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c1"),
                            URI.create("https://c1.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c2"),
                            URI.create("https://c2.app1.tenant1.us-west-1.vespa.oath.cloud/")),
                     tester.controllerTester().controller().applications().clusterEndpoints(context.deploymentIdIn(zone1)));
    }

    @Test
    public void manual_deployment_creates_routing_policy() {
        // Empty application package is valid in manually deployed environments
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        var emptyApplicationPackage = new ApplicationPackageBuilder().build();
        var zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().serviceRegistry().zoneRegistry().setZones(ZoneApiMock.from(zone.environment(), zone.region()));
        tester.provisionLoadBalancers(1, context.instanceId(), zone);

        // Deploy to dev
        tester.controllerTester().controller().applications().deploy(context.instanceId(), zone, Optional.of(emptyApplicationPackage), DeployOptions.none());
        assertEquals("DeploymentSpec is not persisted", DeploymentSpec.empty, context.application().deploymentSpec());
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(context.instanceId()).size());
        assertEquals(Set.of("c0.app1.tenant1.us-east-1.dev.vespa.oath.cloud"), tester.recordNames());
    }

    @Test
    public void manual_deployment_creates_routing_policy_with_non_empty_spec() {
        // Initial deployment
        var tester = new RoutingPoliciesTester();
        var context = tester.newDeploymentContext("tenant1", "app1", "default");
        context.submit(applicationPackage).deploy();
        var zone = ZoneId.from("dev", "us-east-1");
        tester.controllerTester().serviceRegistry().zoneRegistry().setZones(ZoneApiMock.from(zone.environment(), zone.region()));

        // Deploy to dev under different instance
        var devInstance = context.application().id().instance("user");
        tester.provisionLoadBalancers(1, devInstance, zone);
        tester.controllerTester().controller().applications().deploy(devInstance, zone, Optional.of(applicationPackage), DeployOptions.none());
        assertEquals("DeploymentSpec is persisted", applicationPackage.deploymentSpec(), context.application().deploymentSpec());
        context.flushDnsUpdates();

        // Routing policy is created and DNS is updated
        assertEquals(1, tester.policiesOf(devInstance).size());
        assertEquals(Set.of("c0.user.app1.tenant1.us-east-1.dev.vespa.oath.cloud"), tester.recordNames());
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
            this(new DeploymentTester());
        }

        public DeploymentContext newDeploymentContext(String tenant, String application, String instance) {
            return tester.newDeploymentContext(tenant, application, instance);
        }

        public ControllerTester controllerTester() {
            return tester.controllerTester();
        }

        public RoutingPoliciesTester(DeploymentTester tester) {
            this.tester = tester;
        }

        private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, ZoneId... zones) {
            for (ZoneId zone : zones) {
                tester.configServer().removeLoadBalancers(application, zone);
                tester.configServer().addLoadBalancers(zone, createLoadBalancers(zone, application, clustersPerZone));
            }
        }

        private Set<RoutingPolicy> policiesOf(ApplicationId instance) {
            return tester.controller().curator().readRoutingPolicies(instance);
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

    }

}
