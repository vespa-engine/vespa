// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.nio.channels.SocketChannel;


/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
public interface CryptoEngine extends AutoCloseable {
    CryptoSocket createClientCryptoSocket(SocketChannel channel, Spec spec);
    CryptoSocket createServerCryptoSocket(SocketChannel channel);
    static CryptoEngine createDefault() {
        if (!TransportSecurityUtils.isTransportSecurityEnabled()) {
            return new NullCryptoEngine();
        }
        TlsContext tlsContext = TransportSecurityUtils.getSystemTlsContext().get();
        TlsCryptoEngine tlsCryptoEngine = new TlsCryptoEngine(tlsContext);
        MixedMode mixedMode = TransportSecurityUtils.getInsecureMixedMode();
        switch (mixedMode) {
            case DISABLED:
                return tlsCryptoEngine;
            case PLAINTEXT_CLIENT_MIXED_SERVER:
                return new MaybeTlsCryptoEngine(tlsCryptoEngine, false);
            case TLS_CLIENT_MIXED_SERVER:
                return new MaybeTlsCryptoEngine(tlsCryptoEngine, true);
            default:
                throw new IllegalArgumentException(mixedMode.toString());
        }
    }

    @Override
    default void close() {

    }
}
