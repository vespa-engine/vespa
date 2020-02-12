// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.nio.channels.SocketChannel;

/**
 * A crypto engine that supports both tls encrypted connections and
 * unencrypted connections. The use of tls for incoming connections is
 * auto-detected using clever heuristics. The use of tls for outgoing
 * connections is controlled by the useTlsWhenClient flag given to the
 * constructor.
 */
public class MaybeTlsCryptoEngine implements CryptoEngine {

    private final TlsCryptoEngine tlsEngine;
    private final boolean useTlsWhenClient;

    public MaybeTlsCryptoEngine(TlsCryptoEngine tlsEngine, boolean useTlsWhenClient) {
        this.tlsEngine = tlsEngine;
        this.useTlsWhenClient = useTlsWhenClient;
    }

    @Override
    public CryptoSocket createClientCryptoSocket(SocketChannel channel, Spec spec) {
        if (useTlsWhenClient) {
            return tlsEngine.createClientCryptoSocket(channel, spec);
        } else {
            return new NullCryptoSocket(channel, false);
        }
    }

    @Override
    public CryptoSocket createServerCryptoSocket(SocketChannel channel) {
        return new MaybeTlsCryptoSocket(channel, tlsEngine);
    }

    @Override
    public String toString() { return "MaybeTlsCryptoEngine(useTlsWhenClient:" + useTlsWhenClient + ")"; }

    @Override
    public void close() {
        tlsEngine.close();
    }
}
