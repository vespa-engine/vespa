// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.channels.SocketChannel;


/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
public interface CryptoEngine {
    public CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer);
    static public CryptoEngine createDefault() { return new NullCryptoEngine(); }
}
