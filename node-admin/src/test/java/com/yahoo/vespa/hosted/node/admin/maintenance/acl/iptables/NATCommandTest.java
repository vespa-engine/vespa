// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Test DNAT and SNAT Commands
 *
 * @author smorgrav
 */
public class NATCommandTest {

    @Test
    public void sampleNATCommandIPv6() throws UnknownHostException{
        InetAddress externalIP = Inet6Address.getByName("2001:db8::1");
        InetAddress internalIP = Inet6Address.getByName("2001:db8::2");
        String iface = "eth0";

        NATCommand command = new NATCommand(externalIP, internalIP, iface);
        Assert.assertEquals("ip6tables -t nat -A POSTROUTING -o eth0 -s 2001:db8:0:0:0:0:0:2 -j SNAT --to 2001:db8:0:0:0:0:0:1; ip6tables -t nat -A PREROUTING -i eth0 -d 2001:db8:0:0:0:0:0:1 -j DNAT --to-destination 2001:db8:0:0:0:0:0:2", command.asString());
    }

    @Test
    public void sampleNATCommandIPv4() throws UnknownHostException{
        InetAddress externalIP = Inet4Address.getByName("192.168.0.1");
        InetAddress internalIP = Inet4Address.getByName("192.168.0.2");
        String iface = "eth0";

        NATCommand command = new NATCommand(externalIP, internalIP, iface);
        Assert.assertEquals("iptables -t nat -A POSTROUTING -o eth0 -s 192.168.0.2 -j SNAT --to 192.168.0.1; iptables -t nat -A PREROUTING -i eth0 -d 192.168.0.1 -j DNAT --to-destination 192.168.0.2", command.asString());
    }
}
