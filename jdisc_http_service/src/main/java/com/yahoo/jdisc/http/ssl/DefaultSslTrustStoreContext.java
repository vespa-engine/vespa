// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.KeyStore;
import java.util.function.Consumer;

/**
 * @author bjorncs
 */
public class DefaultSslTrustStoreContext implements SslTrustStoreContext {

    private final SslContextFactory sslContextFactory;

    public DefaultSslTrustStoreContext(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public void updateTrustStore(KeyStore trustStore) {
        updateTrustStore(trustStore, null);
    }

    @Override
    public void updateTrustStore(KeyStore trustStore, String password) {
        updateTrustStore(sslContextFactory -> {
            sslContextFactory.setTrustStore(trustStore);
            if (password != null) {
                sslContextFactory.setTrustStorePassword(password);
            }
        });
    }

    @Override
    public void updateTrustStore(String trustStorePath, String trustStoreType, String trustStorePassword) {
        updateTrustStore(sslContextFactory -> {
            sslContextFactory.setTrustStorePath(trustStorePath);
            sslContextFactory.setTrustStoreType(trustStoreType);
            if (trustStorePassword != null) {
                sslContextFactory.setTrustStorePassword(trustStorePassword);
            }
        });
    }

    private void updateTrustStore(Consumer<SslContextFactory> reloader) {
        try {
            sslContextFactory.reload(reloader);
        } catch (Exception e) {
            throw new RuntimeException("Could not update truststore: " + e.getMessage(), e);
        }
    }

}
