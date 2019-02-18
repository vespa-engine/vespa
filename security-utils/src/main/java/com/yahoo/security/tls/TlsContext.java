// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * A simplified version of {@link SSLContext} modelled as an interface.
 *
 * @author bjorncs
 */
public interface TlsContext extends AutoCloseable {

    SSLContext context();

    SSLParameters parameters();

    SSLEngine createSslEngine();

    SSLEngine createSslEngine(String peerHost, int peerPort);

    @Override default void close() {}

}
