// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
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

    private final Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
    private final Application app2 = tester.createApplication("app2", "tenant1", 1, 1L);
    private final Instance instance1 = tester.defaultInstance(app1.id());
    private final Instance instance2 = tester.defaultInstance(app2.id());

    private final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private final ZoneId zone3 = ZoneId.from("prod", "us-east-3");

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .region(zone1.region())
            .region(zone2.region())
            .build();

    @Test
    public void maintains_global_routing_policies() {
        long buildNumber = BuildJob.defaultBuildNumber;
        int clustersPerZone = 2;
        int numberOfDeployments = 2;
        var applicationPackage = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .endpoint("r1", "c0", "us-west-1")
                .endpoint("r2", "c1")
                .build();
        provisionLoadBalancers(clustersPerZone, instance1.id(), zone1, zone2);

        // Creates alias records
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
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
                     tester.controller().applications().routingPolicies().get(instance1.id()).size());

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
        provisionLoadBalancers(clustersPerZone, instance1.id(), zone3);
        tester.deployCompletely(app1, applicationPackage2, ++buildNumber);

        // Endpoint is updated to contain cluster in new deployment
        assertEquals(endpoint1 + " points to c0 in all regions",
                     List.of("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1",
                             "lb-0--tenant1:app1:default--prod.us-east-3/dns-zone-1/prod.us-east-3",
                             "lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1"),
                     aliasDataOf(endpoint1));

        // Another application is deployed with a single cluster and global endpoint
        var endpoint4 = "r0.app2.tenant1.global.vespa.oath.cloud";
        provisionLoadBalancers(1, instance2.id(), zone1, zone2);
        var applicationPackage3 = new ApplicationPackageBuilder()
                .region(zone1.region())
                .region(zone2.region())
                .endpoint("r0", "c0")
                .build();
        tester.deployCompletely(app2, applicationPackage3);
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
        tester.deployCompletely(app1, applicationPackage4, ++buildNumber);
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint1));
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint2));
        assertEquals("DNS records are removed", List.of(), aliasDataOf(endpoint3));
        Set<RoutingPolicy> policies = tester.controller().curator().readRoutingPolicies(instance1.id());
        assertEquals(clustersPerZone * numberOfDeployments, policies.size());
        assertTrue("Rotation membership is removed from all policies",
                   policies.stream().allMatch(policy -> policy.endpoints().isEmpty()));
        assertEquals("Rotations for " + app2 + " are not removed", 2, aliasDataOf(endpoint4).size());
    }

    @Test
    public void maintains_routing_policies_per_zone() {
        // Deploy application
        int clustersPerZone = 2;
        int buildNumber = 42;
        provisionLoadBalancers(clustersPerZone, instance1.id(), zone1, zone2);
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);

        // Deployment creates records and policies for all clusters in all zones
        Set<String> expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(instance1).size());

        // Next deploy does nothing
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(instance1).size());

        // Add 1 cluster in each zone and deploy
        provisionLoadBalancers(clustersPerZone + 1, instance1.id(), zone1, zone2);
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c2.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(6, policies(instance1).size());

        // Deploy another application
        provisionLoadBalancers(clustersPerZone, instance2.id(), zone1, zone2);
        tester.deployCompletely(app2, applicationPackage, ++buildNumber);
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
        assertEquals(4, policies(instance2).size());

        // Deploy removes cluster from app1
        provisionLoadBalancers(clustersPerZone, instance1.id(), zone1, zone2);
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
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
        tester.controller().applications().requireInstance(instance2.id()).deployments().keySet()
              .forEach(zone -> {
                  tester.configServer().removeLoadBalancers(instance2.id(), zone);
                  tester.controller().applications().deactivate(instance2.id(), zone);
              });
        tester.flushDnsRequests();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertTrue("Removes stale routing policies " + app2, tester.controller().applications().routingPolicies().get(instance2.id()).isEmpty());
        assertEquals("Keeps routing policies for " + app1, 4, tester.controller().applications().routingPolicies().get(instance1.id()).size());
    }

    @Test
    public void cluster_endpoints_resolve_from_policies() {
        provisionLoadBalancers(3, instance1.id(), zone1);
        tester.deployCompletely(app1, applicationPackage);
        tester.controllerTester().serviceRegistry().routingGeneratorMock().putEndpoints(new DeploymentId(instance1.id(), zone1), Collections.emptyList());
        assertEquals(Map.of(ClusterSpec.Id.from("c0"),
                            URI.create("https://c0.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c1"),
                            URI.create("https://c1.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c2"),
                            URI.create("https://c2.app1.tenant1.us-west-1.vespa.oath.cloud/")),
                     tester.controller().applications().clusterEndpoints(new DeploymentId(instance1.id(), zone1)));
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
