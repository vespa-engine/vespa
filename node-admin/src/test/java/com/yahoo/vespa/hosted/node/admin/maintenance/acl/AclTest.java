package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AclTest {

    private final Acl aclCommon = new Acl(
            createPortList(1234, 453),
            createTrustedNodes("192.1.2.2", "fb00::1", "fe80::2"));

    private final Acl aclNoPorts = new Acl(
            Collections.emptyList(),
            createTrustedNodes("192.1.2.2", "fb00::1", "fe80::2"));

    @Test
    public void no_trusted_ports() {
        String listRulesIpv4 = aclNoPorts.toRules(IPVersion.IPv4);
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
        String listRulesIpv4 = aclCommon.toRules(IPVersion.IPv4);
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
        String listRulesIpv6 = aclCommon.toRules(IPVersion.IPv6);
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
                        "-A INPUT -j REJECT --reject-with icmp-port-unreachable", listRulesIpv6);
    }

    private List<Integer> createPortList(Integer... ports) {
        return Arrays.asList(ports);
    }

    private List<InetAddress> createTrustedNodes(String... addresses) {
        return Arrays.stream(addresses)
                .map(InetAddresses::forString)
                .collect(Collectors.toList());
    }
}
