// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * A provider that is used to configure SSL connectors in JDisc
 *
 * @author bjorncs
 */
public interface SslContextFactoryProvider extends AutoCloseable {

    /**
     * This method is called once for each SSL connector.
     *
     * @return returns an instance of {@link SslContextFactory} for a given JDisc http server
     */
    SslContextFactory getInstance(String containerId, int port);

    @Override default void close() {}
}
