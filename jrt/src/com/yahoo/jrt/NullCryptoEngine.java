// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.channels.SocketChannel;


/**
 * CryptoEngine implementation that performs no encryption.
 **/
public class NullCryptoEngine implements CryptoEngine {
    @Override public CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer) {
        return new NullCryptoSocket(channel, isServer);
    }
}
