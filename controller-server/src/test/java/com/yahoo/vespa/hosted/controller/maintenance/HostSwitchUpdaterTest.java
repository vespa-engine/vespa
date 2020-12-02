// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.entity.NodeEntity;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class HostSwitchUpdaterTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
        addNodeEntities(tester);

        // First iteration patches all hosts
        HostSwitchUpdater maintainer = new HostSwitchUpdater(tester.controller(), Duration.ofDays(1));
        maintainer.maintain();
        List<Node> nodes = allNodes(tester);
        assertFalse(nodes.isEmpty());
        for (var node : nodes) {
            assertEquals("Node " + node.hostname().value() + (node.type().isHost() ? " has" : " does not have")
                         + " switch hostname", node.type().isHost(), node.switchHostname().isPresent());
            if (node.type().isHost()) {
                assertEquals("tor-" + node.hostname().value(), node.switchHostname().get());
            }
        }

        // Second iteration does not patch anything as all switch information is current
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(false);
        maintainer.maintain();

        // One host is moved to a different switch
        Node host = allNodes(tester).stream().filter(node -> node.type().isHost()).findFirst().get();
        String newSwitch = "tor2-" + host.hostname().value();
        NodeEntity nodeEntity = new NodeEntity(host.hostname().value(), "", "", newSwitch);
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);

        // Host is updated
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
        maintainer.maintain();
        Supplier<Node> updatedHost = () -> allNodes(tester).stream().filter(node -> node.hostname().equals(host.hostname())).findFirst().get();
        assertEquals(newSwitch, updatedHost.get().switchHostname().get());

        // Host keeps old switch hostname if removed from the node entity
        nodeEntity = new NodeEntity(host.hostname().value(), "", "", "");
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);
        maintainer.maintain();
        assertEquals(newSwitch, updatedHost.get().switchHostname().get());
    }

    private static List<Node> allNodes(ControllerTester tester) {
        List<Node> nodes = new ArrayList<>();
        for (var zone : tester.zoneRegistry().zones().controllerUpgraded().all().ids()) {
            nodes.addAll(tester.serviceRegistry().configServer().nodeRepository().list(zone));
        }
        return nodes;
    }

    private static void addNodeEntities(ControllerTester tester) {
        for (var node : allNodes(tester)) {
            if (!node.type().isHost()) continue;
            NodeEntity nodeEntity = new NodeEntity(node.hostname().value(), "", "", "tor-" + node.hostname().value());
            tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);
        }
    }

}
