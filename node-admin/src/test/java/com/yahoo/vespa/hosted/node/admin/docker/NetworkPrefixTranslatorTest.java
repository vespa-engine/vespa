// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @author smorgrav
 */
package com.yahoo.vespa.hosted.node.admin.docker;

import org.junit.Assert;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkPrefixTranslatorTest {

    @Test
    public void translator_with_valid_parameters() throws UnknownHostException {

        // Test simplest possible address
        Inet6Address original = (Inet6Address)InetAddress.getByName("2001:db8::1");
        Inet6Address prefix = (Inet6Address)InetAddress.getByName("fd00::");
        Inet6Address translated = NetworkPrefixTranslator.translate(original, prefix, 64);
        Assert.assertEquals("fd00:0:0:0:0:0:0:1", translated.getHostAddress());


        // Test an actual aws address we use
        original = (Inet6Address)InetAddress.getByName("2600:1f16:f34:5300:ccc6:1703:b7c2:369d");
        translated = NetworkPrefixTranslator.translate(original, prefix, 64);
        Assert.assertEquals("fd00:0:0:0:ccc6:1703:b7c2:369d", translated.getHostAddress());

        // Test different subnet size
        translated = NetworkPrefixTranslator.translate(original, prefix, 48);
        Assert.assertEquals("fd00:0:0:5300:ccc6:1703:b7c2:369d", translated.getHostAddress());
    }
}
