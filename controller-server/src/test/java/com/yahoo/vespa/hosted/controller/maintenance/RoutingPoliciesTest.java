// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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

    private final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private final ZoneId zone2 = ZoneId.from("prod", "us-central-1");
    private final ZoneId zone3 = ZoneId.from("prod", "us-east-3");

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .region(zone1.region())
            .region(zone2.region())
            .build();

    @Test
    public void maintains_global_routing_policies() {
        int buildNumber = 42;
        int clustersPerZone = 2;
        // Cluster 0 is member of 2 global rotations
        Map<Integer, Set<RotationName>> rotations = Map.of(0, Set.of(RotationName.from("r0"), RotationName.from("r1")));
        provisionLoadBalancers(clustersPerZone, rotations, app1.id(), zone1, zone2);

        // Creates alias records for cluster0
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
        Supplier<List<Record>> records1 = () -> tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from("r0.app1.tenant1.global.vespa.oath.cloud"));
        Supplier<List<Record>> records2 = () -> tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from("r1.app1.tenant1.global.vespa.oath.cloud"));
        assertEquals(2, records1.get().size());
        assertEquals(records1.get().size(), records2.get().size());
        assertEquals("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1", records1.get().get(0).data().asString());
        assertEquals("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1", records1.get().get(1).data().asString());
        assertEquals("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1", records2.get().get(0).data().asString());
        assertEquals("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1", records2.get().get(1).data().asString());
        assertEquals(2, tester.controller().applications().routingPolicies().get(app1.id()).iterator().next()
                              .rotationEndpointsIn(SystemName.main).asList().size());

        // Applications gains a new deployment
        ApplicationPackage updatedApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region(zone1.region())
                .region(zone2.region())
                .region(zone3.region())
                .build();
        int numberOfDeployments = 3;
        provisionLoadBalancers(clustersPerZone, rotations, app1.id(), zone3);
        tester.deployCompletely(app1, updatedApplicationPackage, ++buildNumber);

        // Cluster in new deployment is added to the rotation
        assertEquals(numberOfDeployments, records1.get().size());
        assertEquals("lb-0--tenant1:app1:default--prod.us-central-1/dns-zone-1/prod.us-central-1", records1.get().get(0).data().asString());
        assertEquals("lb-0--tenant1:app1:default--prod.us-east-3/dns-zone-1/prod.us-east-3", records1.get().get(1).data().asString());
        assertEquals("lb-0--tenant1:app1:default--prod.us-west-1/dns-zone-1/prod.us-west-1", records1.get().get(2).data().asString());

        // Another application is deployed
        Supplier<List<Record>> records3 = () -> tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from("r0.app2.tenant1.global.vespa.oath.cloud"));
        provisionLoadBalancers(1, Map.of(0, Set.of(RotationName.from("r0"))), app2.id(), zone1, zone2);
        tester.deployCompletely(app2, applicationPackage);
        assertEquals(2, records3.get().size());
        assertEquals("lb-0--tenant1:app2:default--prod.us-central-1/dns-zone-1/prod.us-central-1", records3.get().get(0).data().asString());
        assertEquals("lb-0--tenant1:app2:default--prod.us-west-1/dns-zone-1/prod.us-west-1", records3.get().get(1).data().asString());

        // All rotations for app1 are removed
        provisionLoadBalancers(clustersPerZone, Map.of(), app1.id(), zone1, zone2, zone3);
        tester.deployCompletely(app1, updatedApplicationPackage, ++buildNumber);
        assertEquals(List.of(), records1.get());
        Set<RoutingPolicy> policies = tester.controller().curator().readRoutingPolicies(app1.id());
        assertEquals(clustersPerZone * numberOfDeployments, policies.size());
        assertTrue("Rotation membership is removed from all policies",
                   policies.stream().allMatch(policy -> policy.endpoints().isEmpty()));
        assertEquals("Rotations for " + app2 + " are not removed", 2, records3.get().size());
    }

    @Test
    public void maintains_routing_policies_per_zone() {
        // Deploy application
        int clustersPerZone = 2;
        int buildNumber = 42;
        provisionLoadBalancers(clustersPerZone, app1.id(), zone1, zone2);
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);

        // Deployment creates records and policies for all clusters in all zones
        Set<String> expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(app1).size());

        // Next deploy does nothing
        tester.deployCompletely(app1, applicationPackage, ++buildNumber);
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(app1).size());

        // Add 1 cluster in each zone and deploy
        provisionLoadBalancers(clustersPerZone + 1, app1.id(), zone1, zone2);
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
        assertEquals(6, policies(app1).size());

        // Deploy another application
        provisionLoadBalancers(clustersPerZone, app2.id(), zone1, zone2);
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
        assertEquals(4, policies(app2).size());

        // Deploy removes cluster from app1
        provisionLoadBalancers(clustersPerZone, app1.id(), zone1, zone2);
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
        tester.controller().applications().require(app2.id()).deployments().keySet()
              .forEach(zone -> {
                  tester.configServer().removeLoadBalancers(app2.id(), zone);
                  tester.controller().applications().deactivate(app2.id(), zone);
              });
        tester.flushDnsRequests();
        expectedRecords = Set.of(
                "c0.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-west-1.vespa.oath.cloud",
                "c0.app1.tenant1.us-central-1.vespa.oath.cloud",
                "c1.app1.tenant1.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertTrue("Removes stale routing policies " + app2, tester.controller().applications().routingPolicies().get(app2.id()).isEmpty());
        assertEquals("Keeps routing policies for " + app1, 4, tester.controller().applications().routingPolicies().get(app1.id()).size());
    }

    @Test
    public void cluster_endpoints_resolve_from_policies() {
        provisionLoadBalancers(3, app1.id(), zone1);
        tester.deployCompletely(app1, applicationPackage);
        tester.controllerTester().routingGenerator().putEndpoints(new DeploymentId(app1.id(), zone1), Collections.emptyList());
        assertEquals(Map.of(ClusterSpec.Id.from("c0"),
                            URI.create("https://c0.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c1"),
                            URI.create("https://c1.app1.tenant1.us-west-1.vespa.oath.cloud/"),
                            ClusterSpec.Id.from("c2"),
                            URI.create("https://c2.app1.tenant1.us-west-1.vespa.oath.cloud/")),
                     tester.controller().applications().clusterEndpoints(new DeploymentId(app1.id(), zone1)));
    }

    private Set<RoutingPolicy> policies(Application application) {
        return tester.controller().curator().readRoutingPolicies(application.id());
    }

    private Set<String> recordNames() {
        return tester.controllerTester().nameService().records().stream()
                     .map(Record::name)
                     .map(RecordName::asString)
                     .collect(Collectors.toSet());
    }

    private void provisionLoadBalancers(int clustersPerZone, Map<Integer, Set<RotationName>> clusterRotations, ApplicationId application, ZoneId... zones) {
        for (ZoneId zone : zones) {
            tester.configServer().removeLoadBalancers(application, zone);
            tester.configServer().addLoadBalancers(zone, createLoadBalancers(zone, application, clustersPerZone, clusterRotations));
        }
    }

    private void provisionLoadBalancers(int clustersPerZone, ApplicationId application, ZoneId... zones) {
        provisionLoadBalancers(clustersPerZone, Map.of(), application, zones);
    }

    private static List<LoadBalancer> createLoadBalancers(ZoneId zone, ApplicationId application, int count,
                                                          Map<Integer, Set<RotationName>> clusterRotations) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Set<RotationName> rotations = clusterRotations.getOrDefault(i, Collections.emptySet());
            loadBalancers.add(
                    new LoadBalancer("LB-" + i + "-Z-" + zone.value(),
                                     application,
                                     ClusterSpec.Id.from("c" + i),
                                     HostName.from("lb-" + i + "--" + application.serializedForm() +
                                                   "--" + zone.value()),
                                     Optional.of("dns-zone-1"),
                                     rotations));
        }
        return loadBalancers;
    }

}
