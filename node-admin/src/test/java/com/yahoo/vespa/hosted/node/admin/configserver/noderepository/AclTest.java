package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AclTest {

    private final Acl aclCommon = new Acl(
            createPortSet(1234, 453),
            createTrustedNodes("192.1.2.2", "fb00::1", "fe80::2", "fe80::3"));

    private final Acl aclNoPorts = new Acl(
            Collections.emptySet(),
            createTrustedNodes("192.1.2.2", "fb00::1", "fe80::2"));

    @Test
    public void no_trusted_ports() {
        String listRulesIpv4 = String.join("\n", aclNoPorts.toRules(IPVersion.IPv4));
        Assert.assertEquals(
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
    public void ipv4_list_rules() {
        String listRulesIpv4 = String.join("\n", aclCommon.toRules(IPVersion.IPv4));
        Assert.assertEquals(
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
    public void ipv6_list_rules() {
        String listRulesIpv6 = String.join("\n", aclCommon.toRules(IPVersion.IPv6));
        Assert.assertEquals(
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
    public void ipv6_rules_stable() {
        Acl aclCommonDifferentOrder = new Acl(
                createPortSet(453, 1234),
                createTrustedNodes("fe80::2", "192.1.2.2", "fb00::1", "fe80::3"));

        for (IPVersion ipVersion: IPVersion.values()) {
            Assert.assertEquals(aclCommon.toRules(ipVersion), aclCommonDifferentOrder.toRules(ipVersion));
        }
    }

    private Set<Integer> createPortSet(Integer... ports) {
        return Stream.of(ports).collect(Collectors.toSet());
    }

    private Set<Acl.Node> createTrustedNodes(String... addresses) {
        return Arrays.stream(addresses)
                .map(ipAddress -> new Acl.Node("hostname", ipAddress))
                .collect(Collectors.toSet());
    }
}
