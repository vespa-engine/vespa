// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.security.X509SslContext;
import com.yahoo.security.tls.TlsContext;

import javax.net.ssl.SSLContext;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provider for Vespa {@link SSLContext} instance to Zookeeper.
 *
 * @author bjorncs
 */
public class VespaSslContextProvider implements Supplier<SSLContext> {

    private static TlsContext tlsContext;

    @Override
    public SSLContext get() {
        return tlsContext().orElseThrow(() -> new IllegalStateException("Vespa TLS is not enabled")).context();
    }

    public Optional<X509SslContext> tlsContext() {
        synchronized (VespaSslContextProvider.class) {
            return Optional.ofNullable(tlsContext.sslContext());
        }
    }

    static synchronized void set(TlsContext ctx) {
        if (tlsContext != null) tlsContext.close();
        tlsContext = ctx;
    }

}
