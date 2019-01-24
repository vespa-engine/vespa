// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.channels.SocketChannel;


/**
 * Very simple crypto engine that requires connection handshaking and
 * data transformation. Used to test encryption integration separate
 * from TLS.
 **/
public class XorCryptoEngine implements CryptoEngine {
    @Override public CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer) {
        return new XorCryptoSocket(channel, isServer);
    }
}
