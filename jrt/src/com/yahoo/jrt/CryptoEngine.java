// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.ReloadingTlsContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager.Mode;

import java.nio.channels.SocketChannel;


/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
public interface CryptoEngine extends AutoCloseable {
    CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer);
    static CryptoEngine createDefault() {
        if (!TransportSecurityUtils.isTransportSecurityEnabled()) {
            return new NullCryptoEngine();
        }
        TlsContext tlsContext = new ReloadingTlsContext(TransportSecurityUtils.getConfigFile().get(), Mode.DRY_RUN);
        TlsCryptoEngine tlsCryptoEngine = new TlsCryptoEngine(tlsContext);
        if (!TransportSecurityUtils.isInsecureMixedModeEnabled()) {
            return tlsCryptoEngine;
        }
        MixedMode mixedMode = TransportSecurityUtils.getInsecureMixedMode().get();
        switch (mixedMode) {
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
