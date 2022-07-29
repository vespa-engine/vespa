// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.net.InetAddresses;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author smorgrav
 */
public class IPAddressesTest {

    private final IPAddressesMock mock = new IPAddressesMock();

    @Test
    void choose_sitelocal_ipv4_over_public() {
        mock.addAddress("localhost", "38.3.4.2")
                .addAddress("localhost", "10.0.2.2")
                .addAddress("localhost", "fe80::1")
                .addAddress("localhost", "2001::1");

        assertEquals(InetAddresses.forString("10.0.2.2"), mock.getIPv4Address("localhost").get());
    }

    @Test
    void choose_ipv6_public_over_local() {
        mock.addAddress("localhost", "38.3.4.2")
                .addAddress("localhost", "10.0.2.2")
                .addAddress("localhost", "fe80::1")
                .addAddress("localhost", "2001::1");

        assertEquals(InetAddresses.forString("2001::1"), mock.getIPv6Address("localhost").get());
    }

    @Test
    void throws_when_multiple_ipv6_addresses() {
        assertThrows(RuntimeException.class, () -> {
            mock.addAddress("localhost", "2001::1")
                    .addAddress("localhost", "2001::2");
            mock.getIPv6Address("localhost");
        });
    }

    @Test
    void throws_when_multiple_private_ipv4_addresses() {
        assertThrows(RuntimeException.class, () -> {
            mock.addAddress("localhost", "38.3.4.2")
                    .addAddress("localhost", "10.0.2.2")
                    .addAddress("localhost", "10.0.2.3");
            mock.getIPv4Address("localhost");
        });
    }

    @Test
    void translator_with_valid_parameters() {

        // Test simplest possible address
        Inet6Address original = (Inet6Address) InetAddresses.forString("2001:db8::1");
        Inet6Address prefix = (Inet6Address) InetAddresses.forString("fd00::");
        InetAddress translated = IPAddresses.prefixTranslate(original, prefix, 8);
        assertEquals("fd00:0:0:0:0:0:0:1", translated.getHostAddress());


        // Test an actual aws address we use
        original = (Inet6Address) InetAddresses.forString("2600:1f16:f34:5300:ccc6:1703:b7c2:369d");
        translated = IPAddresses.prefixTranslate(original, prefix, 8);
        assertEquals("fd00:0:0:0:ccc6:1703:b7c2:369d", translated.getHostAddress());

        // Test different subnet size
        translated = IPAddresses.prefixTranslate(original, prefix, 6);
        assertEquals("fd00:0:0:5300:ccc6:1703:b7c2:369d", translated.getHostAddress());
    }
}
