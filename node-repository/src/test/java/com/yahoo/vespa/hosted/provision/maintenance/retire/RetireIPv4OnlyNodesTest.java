// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.internal.ConfigFlavor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class RetireIPv4OnlyNodesTest {
    private final RetireIPv4OnlyNodes policy = new RetireIPv4OnlyNodes(null);
    private final List<Flavor> nodeFlavors = initFlavors();

    @Test
    public void testSingleIPv4Address() {
        Node node = createNodeWithAddresses("127.0.0.1");
        assertTrue(policy.shouldRetire(node).isPresent());
    }

    @Test
    public void testSingleIPv6Address() {
        Node node = createNodeWithAddresses("::1");
        assertFalse(policy.shouldRetire(node).isPresent());
    }

    @Test
    public void testMultipleIPv4Address() {
        Node node = createNodeWithAddresses("127.0.0.1", "10.0.0.1", "192.168.0.1");
        assertTrue(policy.shouldRetire(node).isPresent());
    }

    @Test
    public void testMultipleIPv6Address() {
        Node node = createNodeWithAddresses("::1", "::2", "1234:5678:90ab::cdef");
        assertFalse(policy.shouldRetire(node).isPresent());
    }

    @Test
    public void testCombinationAddress() {
        Node node = createNodeWithAddresses("127.0.0.1", "::1", "10.0.0.1", "::2");
        assertFalse(policy.shouldRetire(node).isPresent());
    }

    @Test
    public void testNeverRetireVMs() {
        Node node = createVMWithAddresses("127.0.0.1", "10.0.0.1", "192.168.0.1");
        assertFalse(policy.shouldRetire(node).isPresent());

        node = createNodeWithAddresses("::1", "::2", "1234:5678:90ab::cdef");
        assertFalse(policy.shouldRetire(node).isPresent());

        node = createNodeWithAddresses("127.0.0.1", "::1", "10.0.0.1", "::2");
        assertFalse(policy.shouldRetire(node).isPresent());
    }

    private Node createNodeWithAddresses(String... addresses) {
        Set<String> ipAddresses = Arrays.stream(addresses).collect(Collectors.toSet());
        return Node.create("openstackid", ipAddresses, Collections.emptySet(), "hostname", Optional.empty(),
                Optional.empty(), nodeFlavors.get(0), NodeType.tenant);
    }

    private Node createVMWithAddresses(String... addresses) {
        Set<String> ipAddresses = Arrays.stream(addresses).collect(Collectors.toSet());
        return Node.create("openstackid", ipAddresses, Collections.emptySet(),  "hostname", Optional.empty(),
                Optional.empty(), nodeFlavors.get(1), NodeType.tenant);
    }

    private List<Flavor> initFlavors() {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        flavorConfigBuilder.addFlavor("default", 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Environment.BARE_METAL);
        flavorConfigBuilder.addFlavor("vm", 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Environment.VIRTUAL_MACHINE);
        return flavorConfigBuilder.build().flavor().stream().map(ConfigFlavor::new).collect(Collectors.toList());
    }
}
