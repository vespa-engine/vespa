// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.SslKeyStoreFactory;

/**
 * An SSL key store provider which provides a factory which throws exception on
 * invocation - as no SSL key store is currently provided by default.
 * The purpose of this is to provide a ssl store factory for injection in the case where
 * no secret store component is provided.
 *
 * @author bratseth
 */
public class SslKeyStoreFactoryProvider implements Provider<SslKeyStoreFactory> {

    private static final ThrowingSslKeyStoreFactory instance = new ThrowingSslKeyStoreFactory();

    @Override
    public SslKeyStoreFactory get() { return instance; }

    @Override
    public void deconstruct() { }

    private static final class ThrowingSslKeyStoreFactory implements SslKeyStoreFactory {

        @Override
        public SslKeyStore createKeyStore(ReaderForPath certificateFile, ReaderForPath keyFile) {
            throw new UnsupportedOperationException("A SSL key store factory component is not available");
        }

        @Override
        public SslKeyStore createTrustStore(ReaderForPath certificateFile) {
            throw new UnsupportedOperationException("A SSL key store factory component is not available");
        }

    }

}
