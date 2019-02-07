// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * The default implementation of {@link SslContextFactoryProvider} to be injected into connectors without explicit ssl configuration
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider implements SslContextFactoryProvider {
    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        throw new UnsupportedOperationException();
    }
}