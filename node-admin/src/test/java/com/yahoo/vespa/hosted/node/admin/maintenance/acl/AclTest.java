package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AclTest {

    private final Acl acl = new Acl(
            createPortList(1234, 453),
            createTrustedNodes("192.1.2.2", "fb00::1", "fe80::2"));

    @Test
    public void ipv4_list_rules() {
        String listRulesIpv4 = acl.toListRules(IPVersion.IPv4, Optional.of(InetAddresses.forString("169.254.1.2")));
        Assert.assertEquals(
                "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p icmp -j ACCEPT\n" +
                        "-A INPUT -p tcp -m multiport --dports 1234,453 -j ACCEPT\n" +
                        "-A INPUT -s 192.1.2.2/32 -j ACCEPT\n" +
                        "-A INPUT -j REJECT\n" +
                        "-A OUTPUT -d 169.254.1.2 -j REDIRECT",
                listRulesIpv4);
    }

    @Test
    public void ipv4_restore_command_without_redirect() {
        String restoreCommandIpv4 = acl.toRestoreCommand(IPVersion.IPv4, Optional.empty());

        Assert.assertEquals("*filter\n" +
                "-P INPUT ACCEPT\n" +
                "-P FORWARD ACCEPT\n" +
                "-P OUTPUT ACCEPT\n" +
                "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                "-A INPUT -i lo -j ACCEPT\n" +
                "-A INPUT -p icmp -j ACCEPT\n" +
                "-A INPUT -p tcp -m multiport --dports 1234,453 -j ACCEPT\n" +
                "-A INPUT -s 192.1.2.2/32 -j ACCEPT\n" +
                "-A INPUT -j REJECT\n" +
                "COMMIT\n", restoreCommandIpv4);
    }

    @Test
    public void ipv6_list_rules() {
        String listRulesIpv6 = acl.toListRules(IPVersion.IPv6, Optional.of(InetAddresses.forString("1234::1234")));
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
                        "-A INPUT -j REJECT\n" +
                        "-A OUTPUT -d 1234::1234 -j REDIRECT",
                listRulesIpv6);
    }

    @Test
    public void ipv6_restore_command() {
        String restoreCommandIpv6 = acl.toRestoreCommand(IPVersion.IPv6, Optional.of(InetAddresses.forString("5005:2322:2323:aaaa::1")));

        Assert.assertEquals("*filter\n" +
                        "-P INPUT ACCEPT\n" +
                        "-P FORWARD ACCEPT\n" +
                        "-P OUTPUT ACCEPT\n" +
                        "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT\n" +
                        "-A INPUT -i lo -j ACCEPT\n" +
                        "-A INPUT -p ipv6-icmp -j ACCEPT\n" +
                        "-A INPUT -p tcp -m multiport --dports 1234,453 -j ACCEPT\n" +
                        "-A INPUT -s fb00::1/128 -j ACCEPT\n" +
                        "-A INPUT -s fe80::2/128 -j ACCEPT\n" +
                        "-A INPUT -j REJECT\n" +
                        "-A OUTPUT -d 5005:2322:2323:aaaa::1 -j REDIRECT\n" +
                        "COMMIT\n",
                restoreCommandIpv6);
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
