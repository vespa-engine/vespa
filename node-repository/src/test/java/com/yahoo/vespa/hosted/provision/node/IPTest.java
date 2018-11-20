// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class IPTest {

    @Test
    public void test_natural_order() {
        Set<String> ipAddresses = ImmutableSet.of(
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
                Arrays.asList(
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
                new ArrayList<>(ImmutableSortedSet.copyOf(IP.naturalOrder, ipAddresses))
        );
    }

}
