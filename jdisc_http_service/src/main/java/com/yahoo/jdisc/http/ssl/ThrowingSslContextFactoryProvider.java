// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * A dummy implementation of {@link SslContextFactoryProvider} to be injected into non-ssl connectors
 *
 * @author bjorncs
 */
public class ThrowingSslContextFactoryProvider implements SslContextFactoryProvider {
    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        throw new UnsupportedOperationException();
    }
}