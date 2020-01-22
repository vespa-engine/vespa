// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class IPTest {

    private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
    private static final LockedNodeList emptyList = new LockedNodeList(List.of(), () -> {});

    private final MockNameResolver resolver = new MockNameResolver().explicitReverseRecords();

    @Test
    public void test_natural_order() {
        Set<String> ipAddresses = Set.of(
                "192.168.254.1",
                "192.168.254.254",
                "127.7.3.1",
                "127.5.254.1",
                "172.16.100.1",
                "172.16.254.2",
                "2001:db8:0:0:0:0:0:ffff",
                "2001:db8:95a3:0:0:0:0:7334",
                "2001:db8:85a3:0:0:8a2e:370:7334",
                "::1",
                "::10",
                "::20");

        assertEquals(
                List.of(
                        "127.5.254.1",
                        "127.7.3.1",
                        "172.16.100.1",
                        "172.16.254.2",
                        "192.168.254.1",
                        "192.168.254.254",
                        "::1",
                        "::10",
                        "::20",
                        "2001:db8:0:0:0:0:0:ffff",
                        "2001:db8:85a3:0:0:8a2e:370:7334",
                        "2001:db8:95a3:0:0:0:0:7334"),
                new ArrayList<>(ImmutableSortedSet.copyOf(IP.NATURAL_ORDER, ipAddresses))
        );
    }

    @Test
    public void test_find_allocation_ipv6_only() {
        IP.Pool pool = createNode(ImmutableSet.of(
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

        Optional<IP.Allocation> allocation = pool.findAllocation(emptyList, resolver);
        assertEquals("::1", allocation.get().primary());
        assertFalse(allocation.get().secondary().isPresent());
        assertEquals("host3", allocation.get().hostname());

        // Allocation fails if DNS record is missing
        resolver.removeRecord("host3");
        try {
            pool.findAllocation(emptyList, resolver);
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals("java.net.UnknownHostException: Could not resolve: host3", e.getMessage());
        }
    }

    @Test
    public void test_find_allocation_ipv4_only() {
        var pool = testPool(false);
        assertTrue(pool instanceof IP.Ipv4Pool);
        var allocation = pool.findAllocation(emptyList, resolver);
        assertFalse("Found allocation", allocation.isEmpty());
        assertEquals("127.0.0.1", allocation.get().primary());
        assertTrue("No secondary address", allocation.get().secondary().isEmpty());
    }

    @Test
    public void test_find_allocation_dual_stack() {
        IP.Pool pool = testPool(true);
        Optional<IP.Allocation> allocation = pool.findAllocation(emptyList, resolver);
        assertEquals("::1", allocation.get().primary());
        assertEquals("127.0.0.2", allocation.get().secondary().get());
        assertEquals("host3", allocation.get().hostname());
    }

    @Test
    public void test_find_allocation_multiple_ipv4_addresses() {
        IP.Pool pool = testPool(true);
        resolver.addRecord("host3", "127.0.0.127");
        try {
            pool.findAllocation(emptyList, resolver);
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
            pool.findAllocation(emptyList, resolver);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Hostnames resolved from each IP address do not point to the same hostname " +
                         "[::1 -> host3, 127.0.0.2 -> host5]", e.getMessage());
        }
    }

    private IP.Pool testPool(boolean dualStack) {
        var addresses = new LinkedHashSet<String>();
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

        return node.ipConfig().pool();
    }

    private static Node createNode(Set<String> ipAddresses) {
        return Node.create("id1", new IP.Config(Set.of("127.0.0.1"), ipAddresses),
                           "host1", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("default"),
                           Optional.empty(), NodeType.host);
    }

}
