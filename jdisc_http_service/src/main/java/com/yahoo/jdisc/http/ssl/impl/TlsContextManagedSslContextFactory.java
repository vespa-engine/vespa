// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.security.tls.TlsContext;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

/**
 * A Jetty {@link SslContextFactory} backed by {@link TlsContext}.
 * Overrides methods that are used by Jetty to construct ssl sockets and ssl engines.
 *
 * @author bjorncs
 */
class TlsContextManagedSslContextFactory extends SslContextFactory.Server {

    private final TlsContext tlsContext;

    TlsContextManagedSslContextFactory(TlsContext tlsContext) {
        this.tlsContext = tlsContext;
    }

    @Override protected void doStart() { } // Override default behaviour
    @Override protected void doStop() { } // Override default behaviour

    @Override
    public SSLEngine newSSLEngine() {
        return tlsContext.createSslEngine();
    }

    @Override
    public SSLEngine newSSLEngine(InetSocketAddress address) {
        return tlsContext.createSslEngine(address.getHostString(), address.getPort());
    }

    @Override
    public SSLEngine newSSLEngine(String host, int port) {
        return tlsContext.createSslEngine(host, port);
    }

}
