// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.entity.NodeEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 * @author bjormel
 */
public class HostInfoUpdaterTest {

    @Test
    void maintain() {
        ControllerTester tester = new ControllerTester();
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
        addNodeEntities(tester);

        // First iteration patches all hosts
        HostInfoUpdater maintainer = new HostInfoUpdater(tester.controller(), Duration.ofDays(1));
        maintainer.maintain();
        List<Node> nodes = allNodes(tester);
        assertFalse(nodes.isEmpty());
        for (var node : nodes) {
            assertEquals(node.type().isHost(), node.switchHostname().isPresent(), "Node " + node.hostname().value() + (node.type().isHost() ? " has" : " does not have")
                    + " switch hostname");
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
        NodeEntity nodeEntity = new NodeEntity(host.hostname().value(), "RD350G", "Lenovo", newSwitch);
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);

        // Host is updated
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
        maintainer.maintain();
        assertEquals(newSwitch, getNode(host.hostname(), tester).switchHostname().get());

        // Host has updated model
        String newModel = "Quanta q801";
        String manufacturer = "quanta computer";
        nodeEntity = new NodeEntity(host.hostname().value(), newModel, manufacturer, newSwitch);
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);

        // Host is updated
        tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
        maintainer.maintain();
        assertEquals(manufacturer + " " + newModel, getNode(host.hostname(), tester).modelName().get());

        // Host keeps old switch hostname if removed from the node entity
        nodeEntity = new NodeEntity(host.hostname().value(), newModel, manufacturer, "");
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);
        maintainer.maintain();
        assertEquals(newSwitch, getNode(host.hostname(), tester).switchHostname().get());

        // Host keeps old model name if removed from the node entity
        nodeEntity = new NodeEntity(host.hostname().value(), "", "", newSwitch);
        tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);
        maintainer.maintain();
        assertEquals(manufacturer + " " + newModel, getNode(host.hostname(), tester).modelName().get());

        // Updates node registered under a different hostname
        ZoneId zone = tester.zoneRegistry().zones().controllerUpgraded().all().ids().get(0);
        String hostnameSuffix = ".prod." + zone.value();
        Node configNode = Node.builder().hostname(HostName.of("cfg3" + hostnameSuffix))
                .type(NodeType.config)
                .build();
        Node configHost = Node.builder().hostname(HostName.of("cfghost3" + hostnameSuffix))
                .type(NodeType.confighost)
                .build();
        tester.serviceRegistry().configServer().nodeRepository().putNodes(zone, List.of(configNode, configHost));
        String switchHostname = switchHostname(configHost);
        NodeEntity configNodeEntity = new NodeEntity("cfg3"  + hostnameSuffix, "RD350G", "Lenovo", switchHostname);
        tester.serviceRegistry().entityService().addNodeEntity(configNodeEntity);
        maintainer.maintain();
        assertEquals(switchHostname, getNode(configHost.hostname(), tester).switchHostname().get());
        assertTrue(getNode(configNode.hostname(), tester).switchHostname().isEmpty(), "Switch hostname is not set for non-host");
    }

    private static Node getNode(HostName hostname, ControllerTester tester) {
        return allNodes(tester).stream()
                               .filter(node -> node.hostname().equals(hostname))
                               .findFirst()
                               .orElseThrow(() -> new IllegalArgumentException("No such node: " + hostname));
    }

    private static List<Node> allNodes(ControllerTester tester) {
        List<Node> nodes = new ArrayList<>();
        for (var zone : tester.zoneRegistry().zones().controllerUpgraded().all().ids()) {
            nodes.addAll(tester.serviceRegistry().configServer().nodeRepository().list(zone, NodeFilter.all()));
        }
        return nodes;
    }

    private static String switchHostname(Node node) {
        return "tor-" + node.hostname().value();
    }

    private static void addNodeEntities(ControllerTester tester) {
        for (var node : allNodes(tester)) {
            if (!node.type().isHost()) continue;
            NodeEntity nodeEntity = new NodeEntity(node.hostname().value(), "RD350G", "Lenovo", switchHostname(node));
            tester.serviceRegistry().entityService().addNodeEntity(nodeEntity);
        }
    }

}
