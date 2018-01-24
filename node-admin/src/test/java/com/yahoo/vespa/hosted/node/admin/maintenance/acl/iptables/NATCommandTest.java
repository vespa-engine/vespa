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

        String insert = NATCommand.insert(externalIP, internalIP);
        Assert.assertEquals("ip6tables -t nat -I POSTROUTING -s 2001:db8:0:0:0:0:0:2 -j SNAT --to 2001:db8:0:0:0:0:0:1 && ip6tables -t nat -I PREROUTING -d 2001:db8:0:0:0:0:0:1 -j DNAT --to-destination 2001:db8:0:0:0:0:0:2 && ip6tables -t nat -I OUTPUT -o lo -d 2001:db8:0:0:0:0:0:1 -j DNAT --to-destination 2001:db8:0:0:0:0:0:2", insert);

        String drop = NATCommand.drop(externalIP, internalIP);
        Assert.assertEquals("ip6tables -t nat -D POSTROUTING -s 2001:db8:0:0:0:0:0:2 -j SNAT --to 2001:db8:0:0:0:0:0:1; ip6tables -t nat -D PREROUTING -d 2001:db8:0:0:0:0:0:1 -j DNAT --to-destination 2001:db8:0:0:0:0:0:2; ip6tables -t nat -D OUTPUT -o lo -d 2001:db8:0:0:0:0:0:1 -j DNAT --to-destination 2001:db8:0:0:0:0:0:2", drop);
    }

    @Test
    public void sampleNATCommandIPv4() throws UnknownHostException{
        InetAddress externalIP = Inet4Address.getByName("192.168.0.1");
        InetAddress internalIP = Inet4Address.getByName("192.168.0.2");

        String insert = NATCommand.insert(externalIP, internalIP);
        Assert.assertEquals("iptables -t nat -I POSTROUTING -s 192.168.0.2 -j SNAT --to 192.168.0.1 && iptables -t nat -I PREROUTING -d 192.168.0.1 -j DNAT --to-destination 192.168.0.2 && iptables -t nat -I OUTPUT -o lo -d 192.168.0.1 -j DNAT --to-destination 192.168.0.2", insert);

        String drop = NATCommand.drop(externalIP, internalIP);
        Assert.assertEquals("iptables -t nat -D POSTROUTING -s 192.168.0.2 -j SNAT --to 192.168.0.1; iptables -t nat -D PREROUTING -d 192.168.0.1 -j DNAT --to-destination 192.168.0.2; iptables -t nat -D OUTPUT -o lo -d 192.168.0.1 -j DNAT --to-destination 192.168.0.2", drop);
    }
}
