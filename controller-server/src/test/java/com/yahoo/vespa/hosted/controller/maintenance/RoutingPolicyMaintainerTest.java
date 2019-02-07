// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicyMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();
    private final Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
    private final RoutingPolicyMaintainer maintainer = new RoutingPolicyMaintainer(tester.controller(), Duration.ofHours(12),
                                                                                   new JobControl(new MockCuratorDb()),
                                                                                   tester.controllerTester().nameService(),
                                                                                   tester.controllerTester().curator());
    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("us-west-1")
            .region("us-central-1")
            .build();

    @Test
    public void maintains_routing_policies_per_zone() {
        // Deploy application
        int clustersPerZone = 2;
        tester.deployCompletely(app1, applicationPackage);
        provisionLoadBalancers(app1, clustersPerZone);

        // Creates records and policies for all clusters in all zones
        maintainer.maintain();
        Set<String> expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, records());
        assertEquals(4, policies(app1).size());

        // Next run does nothing
        maintainer.maintain();
        assertEquals(expectedRecords, records());
        assertEquals(4, policies(app1).size());

        // Add 1 cluster in each zone
        provisionLoadBalancers(app1, clustersPerZone + 1);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, records());
        assertEquals(6, policies(app1).size());

        // Add another application
        Application app2 = tester.createApplication("app2", "tenant1", 1, 1L);
        tester.deployCompletely(app2, applicationPackage);
        provisionLoadBalancers(app2, clustersPerZone);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, records());
        assertEquals(4, policies(app2).size());


        // Remove cluster from app1
        provisionLoadBalancers(app1, clustersPerZone);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, records());

        // Remove app2 completely
        tester.controller().applications().require(app2.id()).deployments().keySet()
              .forEach(zone -> {
                  tester.controller().applications().deactivate(app2.id(), zone);
                  tester.configServer().removeLoadBalancers(app2.id(), zone);
              });
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, records());
    }

    private Set<RoutingPolicy> policies(Application application) {
        return tester.controller().curator().readRoutingPolicies(application.id());
    }

    private Set<String> records() {
        return tester.controllerTester().nameService().records().values().stream()
                     .flatMap(Collection::stream)
                     .map(Record::name)
                     .map(RecordName::asString)
                     .collect(Collectors.toSet());
    }

    private void provisionLoadBalancers(Application application, int numberOfClustersPerZone) {
        tester.controller().applications().require(application.id())
              .deployments().keySet()
              .forEach(zone -> tester.configServer().removeLoadBalancers(application.id(), zone));
        tester.controller().applications().require(application.id())
              .deployments().keySet()
              .forEach(zone -> tester.configServer()
                                     .addLoadBalancers(zone, createLoadBalancers(zone, application.id(), numberOfClustersPerZone)));

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
                                     HostName.from("loadbalancer-" + i + "-" + application.serializedForm() +
                                                   "-zone-" + zone.value()),
                                     Optional.of("dns-zone-1"),
                                     rotations));
        }
        return loadBalancers;
    }

    private static List<LoadBalancer> createLoadBalancers(ZoneId zone, ApplicationId application, int count) {
        return createLoadBalancers(zone, application, count, Collections.emptyMap());
    }

}
