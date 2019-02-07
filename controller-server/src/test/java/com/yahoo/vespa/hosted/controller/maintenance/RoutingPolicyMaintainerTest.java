// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicyMaintainerTest {

    @Test
    public void maintains_routing_policies() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        RoutingPolicyMaintainer maintainer = new RoutingPolicyMaintainer(tester.controller(), Duration.ofHours(12),
                                                                         new JobControl(new MockCuratorDb()),
                                                                         tester.controllerTester().nameService(),
                                                                         tester.controllerTester().curator());

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-central-1")
                .build();

        int numberOfClustersPerZone = 2;

        // Deploy application
        tester.deployCompletely(application, applicationPackage);
        setupClustersWithLoadBalancers(tester, application, numberOfClustersPerZone);

        maintainer.maintain();
        Map<RecordId, Record> records = tester.controllerTester().nameService().records();
        Supplier<Long> recordCount = () -> records.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(4, (long) recordCount.get());

        Set<RoutingPolicy> policies = tester.controller().curator().readRoutingPolicies(application.id());
        assertEquals(4, policies.size());


        // no update
        maintainer.maintain();
        Map<RecordId, Record> records2 = tester.controllerTester().nameService().records();
        assertEquals(4, (long) recordCount.get());
        assertEquals(records, records2);


        // add 1 cluster per zone
        setupClustersWithLoadBalancers(tester, application, numberOfClustersPerZone + 1);

        maintainer.maintain();
        assertEquals(6, (long) recordCount.get());

        Set<RoutingPolicy> policies2 = tester.controller().curator().readRoutingPolicies(application.id());
        assertEquals(6, policies2.size());


        // Add application
        Application application2 = tester.createApplication("app2", "tenant1", 1, 1L);
        tester.deployCompletely(application2, applicationPackage);
        setupClustersWithLoadBalancers(tester, application2, numberOfClustersPerZone);

        maintainer.maintain();
        assertEquals(10, (long) recordCount.get());

        Set<RoutingPolicy> aliases4 = tester.controller().curator().readRoutingPolicies(application2.id());
        assertEquals(4, aliases4.size());


        // Remove cluster in app1
        setupClustersWithLoadBalancers(tester, application, numberOfClustersPerZone);

        maintainer.maintain();
        assertEquals(8, (long) recordCount.get());

        // Remove application app2
        tester.controller().applications().get(application2.id())
              .map(app -> app.deployments().keySet())
              .orElse(Collections.emptySet())
              .forEach(zone -> tester.controller().applications().deactivate(application2.id(), zone));

        maintainer.maintain();
        assertEquals(4, (long) recordCount.get());
    }

    private void setupClustersWithLoadBalancers(DeploymentTester tester, Application application, int numberOfClustersPerZone) {
        tester.controller().applications().get(application.id()).orElseThrow(()->new RuntimeException("No deployments")).deployments().keySet()
                .forEach(zone -> tester.configServer()
                        .removeLoadBalancers(new DeploymentId(application.id(), zone)));
        tester.controller().applications().get(application.id()).orElseThrow(()->new RuntimeException("No deployments")).deployments().keySet()
                .forEach(zone -> tester.configServer()
                        .addLoadBalancers(zone, application.id(), makeLoadBalancers(zone, application.id(), numberOfClustersPerZone)));

    }

    private List<LoadBalancer> makeLoadBalancers(ZoneId zone, ApplicationId application, int count) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        Set<RotationName> rotations = Collections.singleton(RotationName.from("r1"));
        for (int i = 0; i < count; i++) {
            loadBalancers.add(
                    new LoadBalancer("LB-" + i + "-Z-" + zone.value(),
                                     application,
                                     ClusterSpec.Id.from("cluster-" + i),
                                     HostName.from("loadbalancer-" + i + "-" + application.serializedForm() +
                                                   "-zone-" + zone.value()),
                                     Optional.of("dns-zone-1"),
                                     rotations));
        }
        return loadBalancers;
    }

}
