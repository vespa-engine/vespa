// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.loadbalancer.LoadBalancerName;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class LoadBalancerMaintainerTest {

    @Test
    public void maintains_loadbalancer_records_correctly () {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        LoadBalancerMaintainer loadbalancerMaintainer = new LoadBalancerMaintainer(tester.controller(), Duration.ofHours(12),
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

        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records = tester.controllerTester().nameService().records();
        long recordCount = records.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(4,recordCount);

        Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames = tester.controller().curator().readLoadBalancerNames();
        List<LoadBalancerName> names = loadBalancerNames.get(application.id());
        assertEquals(4, names.size());


        // no update
        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records2 = tester.controllerTester().nameService().records();
        long recordCount2 = records2.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(recordCount, recordCount2);
        assertEquals(records, records2);


        // add 1 cluster per zone
        setupClustersWithLoadBalancers(tester, application, numberOfClustersPerZone + 1);

        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records3 = tester.controllerTester().nameService().records();
        long recordCount3 = records3.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(6,recordCount3);

        Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames3 = tester.controller().curator().readLoadBalancerNames();
        List<LoadBalancerName> names3 = loadBalancerNames3.get(application.id());
        assertEquals(6, names3.size());


        // Add application
        Application application2 = tester.createApplication("app2", "tenant1", 1, 1L);
        tester.deployCompletely(application2, applicationPackage);
        setupClustersWithLoadBalancers(tester, application2, numberOfClustersPerZone);

        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records4 = tester.controllerTester().nameService().records();
        long recordCount4 = records4.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(10,recordCount4);

        Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames4 = tester.controller().curator().readLoadBalancerNames();
        List<LoadBalancerName> names4 = loadBalancerNames4.get(application2.id());
        assertEquals(4, names4.size());


        // Remove cluster in app1
        setupClustersWithLoadBalancers(tester, application, numberOfClustersPerZone);

        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records5 = tester.controllerTester().nameService().records();
        long recordCount5 = records5.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(8,recordCount5);

        // Remove application app2
        tester.controller().applications().get(application2.id())
                .map(app -> app.deployments().keySet())
                .orElse(Collections.emptySet())
                .forEach(zone -> tester.controller().applications().deactivate(application2.id(), zone));

        loadbalancerMaintainer.maintain();
        Map<RecordId, Record> records6 = tester.controllerTester().nameService().records();
        long recordCount6 = records6.entrySet().stream().filter(entry -> entry.getValue().data().asString().contains("loadbalancer")).count();
        assertEquals(4,recordCount6);

    }

    private void setupClustersWithLoadBalancers(DeploymentTester tester, Application application, int numberOfClustersPerZone) {
        tester.controller().applications().get(application.id()).orElseThrow(()->new RuntimeException("No deployments")).deployments().keySet()
                .forEach(zone -> tester.configServer()
                        .removeLoadBalancers(new DeploymentId(application.id(), zone)));
        tester.controller().applications().get(application.id()).orElseThrow(()->new RuntimeException("No deployments")).deployments().keySet()
                .forEach(zone -> tester.configServer()
                        .addLoadBalancers(zone, application.id(), getLoadBalancers(zone, application.id(), numberOfClustersPerZone)));

    }


    @Test
    public void test_endpoint_names() {
        ZoneId zoneId = ZoneId.from("prod", "us-north-1");
        ApplicationId withInstanceName = ApplicationId.from("tenant", "application", "instance");
        testLoadBalancerName("instance.application.tenant.prod.us-north-1.vespa.oath.cloud", "default", withInstanceName, zoneId);
        testLoadBalancerName("cluster.instance.application.tenant.prod.us-north-1.vespa.oath.cloud", "cluster", withInstanceName, zoneId);

        ApplicationId withDefaultInstance = ApplicationId.from("tenant", "application", "default");
        testLoadBalancerName("application.tenant.prod.us-north-1.vespa.oath.cloud", "default", withDefaultInstance, zoneId);
        testLoadBalancerName("cluster.application.tenant.prod.us-north-1.vespa.oath.cloud", "cluster", withDefaultInstance, zoneId);
    }

    private void testLoadBalancerName(String expected, String clusterName, ApplicationId applicationId, ZoneId zoneId) {
        assertEquals(expected,
                     LoadBalancerMaintainer.getEndpointName(ClusterSpec.Id.from(clusterName), applicationId, zoneId));
    }

    private List<LoadBalancer> getLoadBalancers(ZoneId zone, ApplicationId applicationId, int loadBalancerCount) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        for (int i = 0; i < loadBalancerCount; i++) {
            loadBalancers.add(
                    new LoadBalancer("LB-"+ i + "-Z-"+zone.value(),
                                     new TenantId(applicationId.tenant().value()),
                                     new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(applicationId.application().value()),
                                     new InstanceId(applicationId.instance().value()),
                                     ClusterSpec.Id.from("cluster-"+i),
                                     HostName.from("loadbalancer-" + i + "-zone-" + zone.value())
                                     ));
        }
        return loadBalancers;
    }
}
