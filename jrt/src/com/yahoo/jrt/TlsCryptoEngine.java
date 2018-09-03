// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

/**
 * A {@link CryptoSocket} that creates {@link TlsCryptoSocket} instances.
 *
 * @author bjorncs
 */
public class TlsCryptoEngine implements CryptoEngine {

    private final SSLContext sslContext;

    public TlsCryptoEngine(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    public TlsCryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer)  {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setNeedClientAuth(true);
        sslEngine.setUseClientMode(!isServer);
        return new TlsCryptoSocket(channel, sslEngine);
    }
}
