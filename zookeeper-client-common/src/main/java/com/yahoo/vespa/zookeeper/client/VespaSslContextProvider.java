// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.client;

import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import javax.net.ssl.SSLContext;
import java.util.function.Supplier;

/**
 * Provider for Vespa {@link SSLContext} instance to Zookeeper + misc utility methods for providing Vespa TLS specific ZK configuration.
 *
 * @author bjorncs
 */
public class VespaSslContextProvider implements Supplier<SSLContext> {

    private static final SSLContext sslContext = TransportSecurityUtils.getSystemTlsContext().map(TlsContext::context).orElse(null);

    @Override
    public SSLContext get() {
        if (sslContext == null) throw new IllegalStateException("Vespa TLS is not enabled");
        return sslContext;
    }

}
