// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.security.tls.TransportSecurityOptions;

import java.nio.channels.SocketChannel;
import java.nio.file.Paths;


/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
public interface CryptoEngine {
    public CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer);
    static public CryptoEngine createDefault() { // TODO Move this logic to a dedicated factory class
        String tlsConfigParameter = System.getenv("VESPA_TLS_CONFIG_FILE");
        if (tlsConfigParameter != null && !tlsConfigParameter.isEmpty()) {
            return new TlsCryptoEngine(TransportSecurityOptions.fromJsonFile(Paths.get(tlsConfigParameter)));
        } else {
            return new NullCryptoEngine();
        }
    }
}
