// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.ProxyServer;
import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class ProxyServerFactoryTestCase {

    @Test(enabled = false)
    public void requireThatProxyServerFactoryWorks() {
        assertNull(ProxyServerFactory.newInstance(null));

        ProxyServer proxy = ProxyServerFactory.newInstance(URI.create("http://localhost:1234"));
        assertEquals(ProxyServer.Protocol.HTTP, proxy.getProtocol());
        assertEquals("localhost", proxy.getHost());
        assertEquals(1234, proxy.getPort());
        assertNull(proxy.getPrincipal());
        assertNull(proxy.getPassword());

        proxy = ProxyServerFactory.newInstance(URI.create("http://foo@localhost:1234"));
        assertEquals(ProxyServer.Protocol.HTTP, proxy.getProtocol());
        assertEquals("localhost", proxy.getHost());
        assertEquals(1234, proxy.getPort());
        assertEquals("foo", proxy.getPrincipal());
        assertNull(proxy.getPassword());

        proxy = ProxyServerFactory.newInstance(URI.create("https://foo:bar@localhost:1234"));
        assertEquals(ProxyServer.Protocol.HTTPS, proxy.getProtocol());
        assertEquals("localhost", proxy.getHost());
        assertEquals(1234, proxy.getPort());
        assertEquals("foo", proxy.getPrincipal());
        assertEquals("bar", proxy.getPassword());
    }
}
