// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.yahoo.jdisc.http.SslProvider;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * A provider that is used to configure SSL connectors in JDisc
 *
 * @deprecated Implement {@link SslProvider} instead
 * @author bjorncs
 */
@Deprecated(forRemoval = true, since = "7")
public interface SslContextFactoryProvider extends AutoCloseable, SslProvider {

    /**
     * This method is called once for each SSL connector.
     *
     * @return returns an instance of {@link SslContextFactory} for a given JDisc http server
     */
    SslContextFactory getInstance(String containerId, int port);

    @Override default void close() {}

    @Override
    default void configureSsl(ConnectorSsl ssl, String name, int port) {
        // Signal that getInstance() should be invoked instead
        throw new UnsupportedOperationException();
    }
}
