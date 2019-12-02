// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 */
public class ClusterInfoMaintainerTest {

    private final ControllerTester tester = new ControllerTester();

    @Test
    public void maintain() {
        tester.createTenant("tenant1", "domain123", 321L);
        ApplicationId app = tester.createApplication("tenant1", "app1", "default").id().defaultInstance();
        ZoneId zone = ZoneId.from("dev", "us-east-1");
        tester.deploy(app, zone);

        // Precondition: no cluster info attached to the deployments
        Deployment deployment = tester.controller().applications().getInstance(app).get().deployments().values().stream()
                                      .findFirst()
                                      .get();
        assertEquals(0, deployment.clusterInfo().size());

        addNodes(zone);
        ClusterInfoMaintainer maintainer = new ClusterInfoMaintainer(tester.controller(), Duration.ofHours(1),
                                                                     new JobControl(new MockCuratorDb()));
        maintainer.maintain();

        deployment = tester.controller().applications().getInstance(app).get().deployments().values().stream()
                           .findFirst()
                           .get();
        assertEquals(2, deployment.clusterInfo().size());
        assertEquals(10, deployment.clusterInfo().get(ClusterSpec.Id.from("clusterA")).getFlavorCost());
    }

    private void addNodes(ZoneId zone) {
        var nodeA = new Node.Builder()
                .hostname(HostName.from("hostA"))
                .parentHostname(HostName.from("parentHostA"))
                .state(Node.State.active)
                .type(NodeType.tenant)
                .owner(ApplicationId.from("tenant1", "app1", "default"))
                .currentVersion(Version.fromString("7.42"))
                .wantedVersion(Version.fromString("7.42"))
                .currentOsVersion(Version.fromString("7.6"))
                .wantedOsVersion(Version.fromString("7.6"))
                .serviceState(Node.ServiceState.expectedUp)
                .resources(new NodeResources(1, 1, 1, 1))
                .cost(10)
                .clusterId("clusterA")
                .clusterType(Node.ClusterType.container)
                .build();
        var nodeB = new Node.Builder()
                .hostname(HostName.from("hostB"))
                .parentHostname(HostName.from("parentHostB"))
                .state(Node.State.active)
                .type(NodeType.tenant)
                .owner(ApplicationId.from("tenant1", "app1", "default"))
                .currentVersion(Version.fromString("7.42"))
                .wantedVersion(Version.fromString("7.42"))
                .currentOsVersion(Version.fromString("7.6"))
                .wantedOsVersion(Version.fromString("7.6"))
                .serviceState(Node.ServiceState.expectedUp)
                .resources(new NodeResources(1, 1, 1, 1))
                .cost(20)
                .clusterId("clusterB")
                .clusterType(Node.ClusterType.container)
                .build();
        tester.configServer().nodeRepository().addNodes(zone, List.of(nodeA, nodeB));
    }

}
