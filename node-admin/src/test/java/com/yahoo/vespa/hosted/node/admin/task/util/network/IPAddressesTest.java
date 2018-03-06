package com.yahoo.vespa.hosted.node.admin.task.util.network;

import org.junit.Assert;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author smorgrav
 */
public class IPAddressesTest {

    private final IPAddressesMock mock = new IPAddressesMock();

    @Test
    public void choose_sitelocal_ipv4_over_public() {
        mock.addAddress("localhost", "38.3.4.2")
                .addAddress("localhost", "10.0.2.2")
                .addAddress("localhost", "fe80::1")
                .addAddress("localhost", "2001::1");

        Assert.assertEquals("10.0.2.2", mock.getIPv4Address("localhost"));
    }

    @Test
    public void choose_ipv6_public_over_local() {
        mock.addAddress("localhost", "38.3.4.2")
                .addAddress("localhost", "10.0.2.2")
                .addAddress("localhost", "fe80::1")
                .addAddress("localhost", "2001::1");

        Assert.assertEquals("2001::1", mock.getIPv6Address("localhost"));
    }

    @Test(expected = RuntimeException.class)
    public void throws_when_multiple_ipv6_addresses() {
        mock.addAddress("localhost", "2001::1")
                .addAddress("localhost", "2001::2");
        mock.getIPv6Address("localhost");
    }

    @Test(expected = RuntimeException.class)
    public void throws_when_multiple_private_ipv4_addresses() {
        mock.addAddress("localhost", "38.3.4.2")
                .addAddress("localhost", "10.0.2.2")
                .addAddress("localhost", "10.0.2.3");
        mock.getIPv4Address("localhost");
    }

    @Test
    public void translator_with_valid_parameters() throws UnknownHostException {

        // Test simplest possible address
        Inet6Address original = (Inet6Address) InetAddress.getByName("2001:db8::1");
        Inet6Address prefix = (Inet6Address) InetAddress.getByName("fd00::");
        InetAddress translated = IPAddresses.prefixTranslate(original, prefix, 64);
        Assert.assertEquals("fd00:0:0:0:0:0:0:1", translated.getHostAddress());


        // Test an actual aws address we use
        original = (Inet6Address) InetAddress.getByName("2600:1f16:f34:5300:ccc6:1703:b7c2:369d");
        translated = IPAddresses.prefixTranslate(original, prefix, 64);
        Assert.assertEquals("fd00:0:0:0:ccc6:1703:b7c2:369d", translated.getHostAddress());

        // Test different subnet size
        translated = IPAddresses.prefixTranslate(original, prefix, 48);
        Assert.assertEquals("fd00:0:0:5300:ccc6:1703:b7c2:369d", translated.getHostAddress());
    }
}
