// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * @author hmusum
 */
public class NodeListMicroBenchmarkTest {

    private static final NodeFlavors nodeFlavors = createNodeFlavors();
    private final NodeResources resources0 = new NodeResources(1, 30, 20, 1.5);
    private int nodeCounter = 0;
    private static final int hostCount = 1000;

    @Ignore
    @Test
    public void testChildrenOf() {
        List<Node> nodes = createHosts();

        List<Node> childNodes = nodes.stream().map(host -> createNodes(host.hostname())).flatMap(Collection::stream).toList();
        nodes.addAll(childNodes);
        NodeList nodeList = new NodeList(nodes, false);

        int iterations = 100000;
        Random random = new Random(0);
        ArrayList<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            indexes.add(random.nextInt(hostCount));
        }
        // Warmup for stable results.
        for (int i = 0; i < 10000; i++) {
            nodeList.childrenOf(nodes.get(indexes.get(i)));
        }

        Instant start = Instant.now();
        for (int i = 0; i < iterations; i++) {
            nodeList.childrenOf(nodes.get(indexes.get(i)));
        }
        Duration duration = Duration.between(start, Instant.now());
        System.out.println("Calling NodeList.childrenOf took " + duration + " (" + duration.toNanos() / iterations / 1000 + " microseconds per invocation)");
    }

    private List<Node> createHosts() {
        List<Node> hosts = new ArrayList<>();
        for (int i = 0; i < hostCount; i++) {
            hosts.add(Node.create("host" + i, IP.Config.of(List.of("::1"), createIps(), List.of()),
                               "host" + i, getFlavor("host"), NodeType.host).build());
        }
        return hosts;
    }

    private List<Node> createNodes(String parentHostname) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodeCounter++;
            Node node = Node.reserve(List.of("::2"), "node" + nodeCounter, parentHostname, resources0, NodeType.tenant).build();
            nodes.add(node);
        }
        return nodes;
    }

    private static NodeFlavors createNodeFlavors() {
        FlavorConfigBuilder builder = new FlavorConfigBuilder();
        builder.addFlavor("host", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        builder.addFlavor("node", 3, 3, 4, 3, Flavor.Type.DOCKER_CONTAINER);
        FlavorsConfig flavorsConfig = builder.build();
        return new NodeFlavors(Optional.ofNullable(flavorsConfig).orElseGet(ProvisioningTester::createConfig));
    }

    private Flavor getFlavor(String name) {
        return nodeFlavors.getFlavor(name).orElseThrow(() -> new RuntimeException("Unknown flavor"));
    }

    private List<String> createIps() {
        // Allow 4 containers
        int start = 2;
        int count = 4;
        var ipAddressPool = new ArrayList<String>();
        for (int i = start; i < (start + count); i++) {
            ipAddressPool.add("::" + i);
        }
        return ipAddressPool;
    }

}
