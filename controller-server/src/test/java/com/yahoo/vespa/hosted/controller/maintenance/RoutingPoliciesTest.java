// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
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

    private final DeploymentTester tester = new DeploymentTester();

    private final DeploymentContext context1 = tester.newDeploymentContext(ApplicationId.from("tenant1", "app1", "default"));
    private final DeploymentContext context2 = tester.newDeploymentContext(ApplicationId.from("tenant1", "app2", "default"));

    private final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private final ZoneId zone3 = ZoneId.from("prod", "us-east-3");

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .region(zone1.region())
            .region(zone2.region())
            .build();

    @Test
    public void maintains_global_routing_policies() {
        int clustersPerZone = 2;
        int numberOfDeployments = 2;
        var applicationPackage = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);

        // Creates alias records
        context1.submit(applicationPackage).deploy();
        var endpoint1 = "r0.app1.tenant1.global.vespa.oath.cloud";
        var endpoint2 = "r1.app1.tenant1.global.vespa.oath.cloud";
        var endpoint3 = "r2.app1.tenant1.global.vespa.oath.cloud";

        assertEquals(endpoint1 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint1));
        assertEquals(endpoint2 + " points to c0 us-west-1",
                     List.of("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint2));
        assertEquals(endpoint3 + " points to c1 in all regions",
                     List.of("lb-1--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-1--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint3));
        assertEquals("Routing policy count is equal to cluster count",
                     numberOfDeployments * clustersPerZone,
                     tester.controller().applications().routingPolicies().get(context1.instanceId()).size());

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
        provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone3);
        context1.submit(applicationPackage2).deploy();

        // Endpoint is updated to contain cluster in new deployment
        assertEquals(endpoint1 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app1:default--prod.us-east-3/dns-zone-1/prod.us-east-3",
                             "lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint1));

        // Another application is deployed with a single cluster and global endpoint
        var endpoint4 = "r0.app2.tenant1.global.vespa.oath.cloud";
        provisionLoadBalancers(1, context2.instanceId(), zone1, zone2);
        var applicationPackage3 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .build();
        context2.submit(applicationPackage3).deploy();
        assertEquals(endpoint4 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app2:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app2:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint4));

        // All endpoints for app1 are removed
        ApplicationPackage applicationPackage4 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context1.submit(applicationPackage4).deploy();
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint1));
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint2));
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint3));
        Set<RoutingPolicy> policies = tester.controller().curator().readRoutingPolicies(context1.instanceId());
        assertEquals(clustersPerZone * numberOfDeployments, policies.size());
        assertTrue("Rotation membership is removed from all policies",
                   policies.stream().allMatch(policy -> policy.endpoints().isEmpty()));
        assertEquals("Rotations for " + context2.application() + " are not removed", 2, aliasDataOf(endpoint4).size());
    }

    @Test
    public void maintains_routing_policies_per_zone() {
        // Deploy application
        int clustersPerZone = 2;
        int buildNumber = 42;
        provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);
        context1.submit(applicationPackage).deploy();

        // Deployment creates records and policies for all clusters in all zones
        Set<String> expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(context1.instance()).size());

        // Next deploy does nothing
        context1.submit(applicationPackage).deploy();
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(context1.instance()).size());

        // Add 1 cluster in each zone and deploy
        provisionLoadBalancers(clustersPerZone + 1, context1.instanceId(), zone1, zone2);
        context1.submit(applicationPackage).deploy();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(6, policies(context1.instance()).size());

        // Deploy another application
        provisionLoadBalancers(clustersPerZone, context2.instanceId(), zone1, zone2);
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
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(context2.instance()).size());

        // Deploy removes cluster from app1
        provisionLoadBalancers(clustersPerZone, context1.instanceId(), zone1, zone2);
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
        assertEquals(expectedRecords, recordNames());

        // Remove app2 completely
        tester.controller().applications().requireInstance(context2.instanceId()).deployments().keySet()
              .forEach(zone -> {
                  tester.configServer().removeLoadBalancers(context2.instanceId(), zone);
                  tester.controller().applications().deactivate(context2.instanceId(), zone);
              });
        context2.flushDnsUpdates();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertTrue("Removes stale routing policies " + context2.application(), tester.controller().applications().routingPolicies().get(context2.instanceId()).isEmpty());
        assertEquals("Keeps routing policies for " + context1.application(), 4, tester.controller().applications().routingPolicies().get(context1.instanceId()).size());
    }

    @Test
    public void cluster_endpoints_resolve_from_policies() {
        provisionLoadBalancers(3, context1.instanceId(), zone1);
        context1.submit(applicationPackage).deploy();
        tester.controllerTester().serviceRegistry().routingGeneratorMock().putEndpoints(context1.deploymentIdIn(zone1), Collections.emptyList());
        assertEquals(Map.of(ClusterSpec.Id.from("c0"),
                            URI.create("https://c0.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c1"),
                            URI.create("https://c1.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c2"),
                            URI.create("https://c2.app1.tenant1.us-west-1.vespa.oath.cloud/")),
                     tester.controller().applications().clusterEndpoints(context1.deploymentIdIn(zone1)));
    }

    private Set<RoutingPolicy> policies(Instance instance) {
        return tester.controller().curator().readRoutingPolicies(instance.id());
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

    private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, ZoneId... zones) {
        for (ZoneId zone : zones) {
            tester.configServer().removeLoadBalancers(application, zone);
            tester.configServer().addLoadBalancers(zone, createLoadBalancers(zone, application, clustersPerZone));
        }
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
        // Add an inactive load balancers that should be ignored
        loadBalancers.add(new LoadBalancer("inactive-LB-0-Z-" + zone.value(),
                                           application,
                                           ClusterSpec.Id.from("c0"),
                                           HostName.from("lb-0--" + application.serializedForm() +
                                                         "--" + zone.value()),
                                           LoadBalancer.State.inactive,
                                           Optional.of("dns-zone-1")));
        return loadBalancers;
    }

}
