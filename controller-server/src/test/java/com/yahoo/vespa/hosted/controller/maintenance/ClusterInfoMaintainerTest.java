// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 */
public class ClusterInfoMaintainerTest {

    private final ControllerTester tester = new ControllerTester();

    @Test
    public void maintain() {
        tester.createTenant("tenant1", "domain123", 321L);
        ApplicationId app = tester.createApplication(TenantName.from("tenant1"), "app1", "default", 123).id().defaultInstance();
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
        var nodeA = new Node(HostName.from("hostA"),
                             Optional.empty(),
                             Node.State.active,
                             NodeType.tenant,
                             Optional.of(ApplicationId.from("tenant1", "app1", "default")),
                             Version.fromString("7.42"),
                             Version.fromString("7.42"),
                             Version.fromString("7.6"),
                             Version.fromString("7.6"),
                             Node.ServiceState.expectedUp,
                             0,
                             0,
                             0,
                             0,
                             24,
                             24,
                             500,
                             1000,
                             false,
                             10,
                             "C-2B/24/500",
                             "clusterA",
                             Node.ClusterType.container);
        var nodeB = new Node(HostName.from("hostB"),
                             Optional.empty(),
                             Node.State.active,
                             NodeType.tenant,
                             Optional.of(ApplicationId.from("tenant1", "app1", "default")),
                             Version.fromString("7.42"),
                             Version.fromString("7.42"),
                             Version.fromString("7.6"),
                             Version.fromString("7.6"),
                             Node.ServiceState.expectedUp,
                             0,
                             0,
                             0,
                             0,
                             40,
                             24,
                             500,
                             1000,
                             false,
                             20,
                             "C-2C/24/500",
                             "clusterB",
                             Node.ClusterType.container);
        tester.configServer().nodeRepository().addNodes(zone, List.of(nodeA, nodeB));
    }

}
