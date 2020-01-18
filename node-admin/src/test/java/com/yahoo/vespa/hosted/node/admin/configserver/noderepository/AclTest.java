// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author smorgrav
 */
public class AclTest {

    private static final Acl aclCommon = new Acl(
            Set.of(1234, 453),
            testNodes("192.1.2.2", "fb00::1", "fe80::2", "fe80::3"),
            Set.of());

    private static final Acl aclWithoutPorts = new Acl(
            Set.of(),
            testNodes("192.1.2.2", "fb00::1", "fe80::2"),
            Set.of());

    @Test
    public void no_trusted_ports() {
        String listRulesIpv4 = String.join("\n", aclWithoutPorts.toRules(IPVersion.IPv4));
        assertEquals(
                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p icmp -j ACCEPT\n" +
                        "-A INPUT -s 192.1.2.2/32 -j ACCEPT\n" +
                        "-A INPUT -j REJECT --reject-with icmp-port-unreachable",
                listRulesIpv4);
    }

    @Test
    public void ipv4_rules() {
        String listRulesIpv4 = String.join("\n", aclCommon.toRules(IPVersion.IPv4));
        assertEquals(
                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p icmp -j ACCEPT\n" +
                        "-A INPUT -p tcp -m multiport --dports 1234,453 -j ACCEPT\n" +
                        "-A INPUT -s 192.1.2.2/32 -j ACCEPT\n" +
                        "-A INPUT -j REJECT --reject-with icmp-port-unreachable",
                listRulesIpv4);
    }

    @Test
    public void ipv6_rules() {
        String listRulesIpv6 = String.join("\n", aclCommon.toRules(IPVersion.IPv6));
        assertEquals(
                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                        "-A INPUT -p tcp -m multiport --dports 1234,453 -j ACCEPT\n" +
                        "-A INPUT -s fb00::1/128 -j ACCEPT\n" +
                        "-A INPUT -s fe80::2/128 -j ACCEPT\n" +
                        "-A INPUT -s fe80::3/128 -j ACCEPT\n" +
                        "-A INPUT -j REJECT --reject-with icmp6-port-unreachable", listRulesIpv6);
    }

    @Test
    public void ipv6_rules_stable_order() {
        Acl aclCommonDifferentOrder = new Acl(
                Set.of(453, 1234),
                testNodes("fe80::2", "192.1.2.2", "fb00::1", "fe80::3"),
                Set.of());

        for (IPVersion ipVersion: IPVersion.values()) {
            assertEquals(aclCommon.toRules(ipVersion), aclCommonDifferentOrder.toRules(ipVersion));
        }
    }

    @Test
    public void trusted_networks() {
        Acl acl = new Acl(Set.of(4080), testNodes("127.0.0.1"), Set.of("10.0.0.0/24", "2001:db8::/32"));

        assertEquals("-P INPUT ACCEPT\n" +
                     "-P FORWARD ACCEPT\n" +
                     "-P OUTPUT ACCEPT\n" +
                     "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                     "-A INPUT -i lo -j ACCEPT\n" +
                     "-A INPUT -p icmp -j ACCEPT\n" +
                     "-A INPUT -p tcp -m multiport --dports 4080 -j ACCEPT\n" +
                     "-A INPUT -s 127.0.0.1/32 -j ACCEPT\n" +
                     "-A INPUT -s 10.0.0.0/24 -j ACCEPT\n" +
                     "-A INPUT -j REJECT --reject-with icmp-port-unreachable",
                     String.join("\n", acl.toRules(IPVersion.IPv4)));

        assertEquals("-P INPUT ACCEPT\n" +
                     "-P FORWARD ACCEPT\n" +
                     "-P OUTPUT ACCEPT\n" +
                     "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                     "-A INPUT -i lo -j ACCEPT\n" +
                     "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                     "-A INPUT -p tcp -m multiport --dports 4080 -j ACCEPT\n" +
                     "-A INPUT -s 2001:db8::/32 -j ACCEPT\n" +
                     "-A INPUT -j REJECT --reject-with icmp6-port-unreachable",
                     String.join("\n", acl.toRules(IPVersion.IPv6)));
    }

    private static Set<Acl.Node> testNodes(String... address) {
        return Arrays.stream(address)
                     .map(a -> new Acl.Node("hostname", a))
                     .collect(Collectors.toUnmodifiableSet());
    }

}
