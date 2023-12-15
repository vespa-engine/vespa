// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.client;

import com.yahoo.vespa.zookeeper.tls.VespaZookeeperTlsContextUtils;

import javax.net.ssl.SSLContext;
import java.util.function.Supplier;

/**
 * Provider for Vespa {@link SSLContext} instance to Zookeeper.
 *
 * @author bjorncs
 */
public class VespaSslContextProvider implements Supplier<SSLContext> {

    @Override
    public SSLContext get() {
        return VespaZookeeperTlsContextUtils.tlsContext()
                                            .orElseThrow(() -> new IllegalStateException("Vespa TLS is not enabled"))
                                            .sslContext().context();
    }

}
