// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.KeyStore;
import java.util.function.Consumer;

/**
 * @author bjorncs
 */
public class DefaultSslKeyStoreContext implements SslKeyStoreContext {

    private final SslContextFactory sslContextFactory;

    public DefaultSslKeyStoreContext(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public void updateKeyStore(KeyStore keyStore) {
        updateKeyStore(keyStore, null);
    }

    @Override
    public void updateKeyStore(KeyStore keyStore, String password) {
        updateKeyStore(sslContextFactory -> {
            sslContextFactory.setKeyStore(keyStore);
            if (password != null) {
                sslContextFactory.setKeyStorePassword(password);
            }
        });
    }

    @Override
    public void updateKeyStore(String keyStorePath, String keyStoreType, String keyStorePassword) {
        updateKeyStore(sslContextFactory -> {
            sslContextFactory.setKeyStorePath(keyStorePath);
            sslContextFactory.setKeyStoreType(keyStoreType);
            sslContextFactory.setKeyStorePassword(keyStorePassword);
        });
    }

    private void updateKeyStore(Consumer<SslContextFactory> reloader) {
        try {
            sslContextFactory.reload(reloader);
        } catch (Exception e) {
            throw new RuntimeException("Could not update keystore: " + e.getMessage(), e);
        }
    }
}
