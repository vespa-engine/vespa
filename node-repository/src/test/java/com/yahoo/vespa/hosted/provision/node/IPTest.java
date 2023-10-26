// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class IPTest {

    private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
    private static final LockedNodeList emptyList = new LockedNodeList(List.of(), () -> {});
    private final MockNameResolver resolver = new MockNameResolver().explicitReverseRecords();

    private IP.Allocation.Context contextOf(boolean exclave) {
        return IP.Allocation.Context.from(CloudName.AWS, exclave, resolver);
    }

    @Test
    public void test_find_allocation_ipv6_only() {
        IP.Pool pool = createNode(List.of(
                "::1",
                "::2",
                "::3"
        )).ipConfig().pool();

        resolver.addRecord("host1", "::2");
        resolver.addRecord("host2", "::3");
        resolver.addRecord("host3", "::1");
        resolver.addReverseRecord("::3", "host2");
        resolver.addReverseRecord("::1", "host3");
        resolver.addReverseRecord("::2", "host1");

        var context = contextOf(false);
        Optional<IP.Allocation> allocation = pool.findAllocation(context, emptyList);
        assertEquals(Optional.of("::1"), allocation.get().ipv6Address());
        assertFalse(allocation.get().ipv4Address().isPresent());
        assertEquals("host3", allocation.get().hostname());

        // Allocation fails if DNS record is missing
        resolver.removeRecord("host3");
        try {
            pool.findAllocation(context, emptyList);
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals("java.net.UnknownHostException: Could not resolve: host3", e.getMessage());
        }
    }

    @Test
    public void test_find_allocation_ipv4_only() {
        var pool = testPool(false);
        var allocation = pool.findAllocation(contextOf(false), emptyList);
        assertFalse("Found allocation", allocation.isEmpty());
        assertEquals(Optional.of("127.0.0.1"), allocation.get().ipv4Address());
        assertTrue("No IPv6 address", allocation.get().ipv6Address().isEmpty());
    }

    @Test
    public void test_find_allocation_dual_stack() {
        IP.Pool pool = testPool(true);
        Optional<IP.Allocation> allocation = pool.findAllocation(contextOf(false), emptyList);
        assertEquals(Optional.of("::1"), allocation.get().ipv6Address());
        assertEquals("127.0.0.2", allocation.get().ipv4Address().get());
        assertEquals("host3", allocation.get().hostname());
    }

    @Test
    public void test_find_allocation_multiple_ipv4_addresses() {
        IP.Pool pool = testPool(true);
        resolver.addRecord("host3", "127.0.0.127");
        try {
            pool.findAllocation(contextOf(false), emptyList);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Hostname host3 resolved to more than 1 IPv4 address: [127.0.0.2, 127.0.0.127]",
                         e.getMessage());
        }
    }

    @Test
    public void test_find_allocation_invalid_ipv4_reverse_record() {
        var pool = testPool(true);
        resolver.removeRecord("127.0.0.2")
                .addReverseRecord("127.0.0.2", "host5");
        try {
            pool.findAllocation(contextOf(false), emptyList);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Hostnames resolved from each IP address do not point to the same hostname " +
                         "[::1 -> host3, 127.0.0.2 -> host5]", e.getMessage());
        }
    }

    @Test
    public void test_enclave() {
        // In Enclave, the hosts and their nodes have only public IPv6 addresses,
        // and DNS has AAAA records without PTR records.

        resolver.addRecord("host1", "2600:1f10:::1")
                .addRecord("node1", "2600:1f10:::2")
                .addRecord("node2", "2600:1f10:::3");

        IP.Config config = IP.Config.of(List.of("2600:1f10:::1"),
                                        List.of("2600:1f10:::2", "2600:1f10:::3"),
                                        List.of(HostName.of("node1"), HostName.of("node2")));
        IP.Pool pool = config.pool();
        Optional<IP.Allocation> allocation = pool.findAllocation(contextOf(true), emptyList);
    }

    private IP.Pool testPool(boolean dualStack) {
        var addresses = new ArrayList<String>();
        addresses.add("127.0.0.1");
        addresses.add("127.0.0.2");
        addresses.add("127.0.0.3");
        if (dualStack) {
            addresses.add("::1");
            addresses.add("::2");
            addresses.add("::3");
        }

        Node node = createNode(addresses);

        // IPv4 addresses
        resolver.addRecord("host1", "127.0.0.3")
                .addRecord("host2", "127.0.0.1")
                .addRecord("host3", "127.0.0.2")
                .addReverseRecord("127.0.0.1", "host2")
                .addReverseRecord("127.0.0.2", "host3")
                .addReverseRecord("127.0.0.3", "host1");

        // IPv6 addresses
        if (dualStack) {
            resolver.addRecord("host1", "::2")
                    .addRecord("host2", "::3")
                    .addRecord("host3", "::1")
                    .addReverseRecord("::3", "host2")
                    .addReverseRecord("::1", "host3")
                    .addReverseRecord("::2", "host1");
        }

        IP.Pool pool = node.ipConfig().pool();
        assertNotEquals(dualStack, pool.ipAddresses().stack() == IP.IpAddresses.Stack.ipv4);
        return pool;
    }

    private static Node createNode(List<String> ipAddresses) {
        return Node.create("id1", IP.Config.of(List.of("127.0.0.1"), ipAddresses),
                           "host1", nodeFlavors.getFlavorOrThrow("default"), NodeType.host).build();
    }

}
