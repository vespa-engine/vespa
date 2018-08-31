// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * A {@link CryptoSocket} that creates {@link SslSocket} instances.
 *
 * @author bjorncs
 */
public class SslEngine implements CryptoEngine {

    private final SSLContext sslContext;

    public SslEngine(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    public SslSocket createCryptoSocket(SocketChannel channel, boolean isServer)  {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setNeedClientAuth(true);
        sslEngine.setUseClientMode(!isServer);
        return new SslSocket(channel, sslEngine);
    }
}
