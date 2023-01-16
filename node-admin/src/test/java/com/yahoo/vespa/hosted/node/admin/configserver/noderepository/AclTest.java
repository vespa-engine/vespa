// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author smorgrav
 */
public class AclTest {

    private static final Acl aclCommon = new Acl(
            Set.of(1234, 453), Set.of(4321),
            testNodes(Set.of(), "192.1.2.2", "fb00::1", "fe80::2", "fe80::3"),
            Set.of());

    private static final Acl aclWithoutPorts = new Acl(
            Set.of(), Set.of(),
            testNodes(Set.of(), "192.1.2.2", "fb00::1", "fe80::2"),
            Set.of());

    @Test
    void no_trusted_ports() {
        String listRulesIpv4 = String.join("\n", aclWithoutPorts.toRules(IPVersion.IPv4));
        assertEquals(
                """
                        -P INPUT ACCEPT
                        -P FORWARD ACCEPT
                        -P OUTPUT ACCEPT
                        -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                        -A INPUT -i lo -j ACCEPT
                        -A INPUT -p icmp -j ACCEPT
                        -A INPUT -s 192.1.2.2/32 -j ACCEPT
                        -A INPUT -j REJECT --reject-with icmp-port-unreachable""",
                listRulesIpv4);
    }

    @Test
    void ipv4_rules() {
        String listRulesIpv4 = String.join("\n", aclCommon.toRules(IPVersion.IPv4));
        assertEquals(
                """
                        -P INPUT ACCEPT
                        -P FORWARD ACCEPT
                        -P OUTPUT ACCEPT
                        -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                        -A INPUT -i lo -j ACCEPT
                        -A INPUT -p icmp -j ACCEPT
                        -A INPUT -p tcp -m multiport --dports 453,1234 -j ACCEPT
                        -A INPUT -p udp -m multiport --dports 4321 -j ACCEPT
                        -A INPUT -s 192.1.2.2/32 -j ACCEPT
                        -A INPUT -j REJECT --reject-with icmp-port-unreachable""",
                listRulesIpv4);
    }

    @Test
    void ipv6_rules() {
        String listRulesIpv6 = String.join("\n", aclCommon.toRules(IPVersion.IPv6));
        assertEquals(
                """
                        -P INPUT ACCEPT
                        -P FORWARD ACCEPT
                        -P OUTPUT ACCEPT
                        -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                        -A INPUT -i lo -j ACCEPT
                        -A INPUT -p ipv6-icmp -j ACCEPT
                        -A INPUT -p tcp -m multiport --dports 453,1234 -j ACCEPT
                        -A INPUT -p udp -m multiport --dports 4321 -j ACCEPT
                        -A INPUT -s fb00::1/128 -j ACCEPT
                        -A INPUT -s fe80::2/128 -j ACCEPT
                        -A INPUT -s fe80::3/128 -j ACCEPT
                        -A INPUT -j REJECT --reject-with icmp6-port-unreachable""", listRulesIpv6);
    }

    @Test
    void ipv6_rules_stable_order() {
        Acl aclCommonDifferentOrder = new Acl(
                Set.of(453, 1234), Set.of(4321),
                testNodes(Set.of(), "fe80::2", "192.1.2.2", "fb00::1", "fe80::3"),
                Set.of());

        for (IPVersion ipVersion : IPVersion.values()) {
            assertEquals(aclCommon.toRules(ipVersion), aclCommonDifferentOrder.toRules(ipVersion));
        }
    }

    @Test
    void trusted_networks() {
        Acl acl = new Acl(Set.of(4080), Set.of(), testNodes(Set.of(), "127.0.0.1"), Set.of("10.0.0.0/24", "2001:db8::/32"));

        assertEquals("""
                        -P INPUT ACCEPT
                        -P FORWARD ACCEPT
                        -P OUTPUT ACCEPT
                        -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                        -A INPUT -i lo -j ACCEPT
                        -A INPUT -p icmp -j ACCEPT
                        -A INPUT -p tcp -m multiport --dports 4080 -j ACCEPT
                        -A INPUT -s 127.0.0.1/32 -j ACCEPT
                        -A INPUT -s 10.0.0.0/24 -j ACCEPT
                        -A INPUT -j REJECT --reject-with icmp-port-unreachable""",
                String.join("\n", acl.toRules(IPVersion.IPv4)));

        assertEquals("""
                        -P INPUT ACCEPT
                        -P FORWARD ACCEPT
                        -P OUTPUT ACCEPT
                        -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                        -A INPUT -i lo -j ACCEPT
                        -A INPUT -p ipv6-icmp -j ACCEPT
                        -A INPUT -p tcp -m multiport --dports 4080 -j ACCEPT
                        -A INPUT -s 2001:db8::/32 -j ACCEPT
                        -A INPUT -j REJECT --reject-with icmp6-port-unreachable""",
                String.join("\n", acl.toRules(IPVersion.IPv6)));
    }

    @Test
    void config_server_acl() {
        Set<Acl.Node> testNodes = Stream.concat(testNodes(NodeType.config, Set.of(), "172.17.0.41", "172.17.0.42", "172.17.0.43").stream(),
                                                testNodes(NodeType.tenant, Set.of(19070), "172.17.0.81", "172.17.0.82", "172.17.0.83").stream())
                                        .collect(Collectors.toSet());
        Acl acl = new Acl(Set.of(22, 4443), Set.of(), testNodes, Set.of());
        assertEquals("""
                             -P INPUT ACCEPT
                             -P FORWARD ACCEPT
                             -P OUTPUT ACCEPT
                             -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                             -A INPUT -i lo -j ACCEPT
                             -A INPUT -p icmp -j ACCEPT
                             -A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT
                             -A INPUT -s 172.17.0.41/32 -j ACCEPT
                             -A INPUT -s 172.17.0.42/32 -j ACCEPT
                             -A INPUT -s 172.17.0.43/32 -j ACCEPT
                             -A INPUT -s 172.17.0.81/32 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -s 172.17.0.82/32 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -s 172.17.0.83/32 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -j REJECT --reject-with icmp-port-unreachable""",
                     String.join("\n", acl.toRules(IPVersion.IPv4)));

        Set<Acl.Node> testNodes2 = Stream.concat(testNodes(NodeType.config, Set.of(), "2001:db8::41", "2001:db8::42", "2001:db8::43").stream(),
                                                 testNodes(NodeType.tenant, Set.of(19070), "2001:db8::81", "2001:db8::82", "2001:db8::83").stream())
                                         .collect(Collectors.toSet());
        Acl acl2 = new Acl(Set.of(22, 4443), Set.of(), testNodes2, Set.of());

        assertEquals("""
                             -P INPUT ACCEPT
                             -P FORWARD ACCEPT
                             -P OUTPUT ACCEPT
                             -A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
                             -A INPUT -i lo -j ACCEPT
                             -A INPUT -p ipv6-icmp -j ACCEPT
                             -A INPUT -p tcp -m multiport --dports 22,4443 -j ACCEPT
                             -A INPUT -s 2001:db8::41/128 -j ACCEPT
                             -A INPUT -s 2001:db8::42/128 -j ACCEPT
                             -A INPUT -s 2001:db8::43/128 -j ACCEPT
                             -A INPUT -s 2001:db8::81/128 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -s 2001:db8::82/128 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -s 2001:db8::83/128 -p tcp -m multiport --dports 19070 -j ACCEPT
                             -A INPUT -j REJECT --reject-with icmp6-port-unreachable""",
                     String.join("\n", acl2.toRules(IPVersion.IPv6)));
    }

    private static Set<Acl.Node> testNodes(Set<Integer> ports, String... address) {
        return testNodes(NodeType.tenant, ports, address);
    }

    private static Set<Acl.Node> testNodes(NodeType nodeType, Set<Integer> ports, String... address) {
        return Arrays.stream(address)
                     .map(addr -> new Acl.Node("hostname", addr, ports))
                     .collect(Collectors.toUnmodifiableSet());
    }

}
