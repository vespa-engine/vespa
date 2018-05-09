// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;

/**
 * Responsible for updating {@link SSLConnectionSocketFactory} on {@link ConfigServerApiImpl} asynchronously
 * using SIA based certificates (through {@link SiaIdentityProvider}).
 *
 * @author bjorncs
 * @author hakon
 */
public class SslConnectionSocketFactoryUpdater implements AutoCloseable {

    private final Object monitor = new Object();
    private final HostnameVerifier configServerHostnameVerifier;
    private final SiaIdentityProvider sia;

    private final Set<ConfigServerApi> configServerApis = new HashSet<>();
    private SSLConnectionSocketFactory socketFactory;

    /**
     * Creates an updater with valid initial {@link SSLConnectionSocketFactory}
     *
     * @throws RuntimeException if e.g. key store options have been specified, but was unable
     *                          create a create a key store with a valid certificate
     */
    public static SslConnectionSocketFactoryUpdater createAndRefreshKeyStoreIfNeeded(SiaIdentityProvider identityProvider,
                                                                                     AthenzIdentity configserverIdentity) {
        return new SslConnectionSocketFactoryUpdater(identityProvider, createHostnameVerifier(configserverIdentity));
    }

    SslConnectionSocketFactoryUpdater(SiaIdentityProvider siaIdentityProvider,
                                      HostnameVerifier configServerHostnameVerifier) {
        this.configServerHostnameVerifier = configServerHostnameVerifier;
        this.sia = siaIdentityProvider;
        if (siaIdentityProvider != null) {
            siaIdentityProvider.addIdentityListener(this::updateSocketFactory);
            socketFactory = createSocketFactory(siaIdentityProvider.getIdentitySslContext());
        } else {
            socketFactory = createDefaultSslConnectionSocketFactory();
        }
    }

    private void updateSocketFactory(SSLContext sslContext, AthenzService identity) {
        synchronized (monitor) {
            socketFactory = createSocketFactory(sslContext);
            configServerApis.forEach(api -> api.setSSLConnectionSocketFactory(socketFactory));
        }
    }

    public SSLConnectionSocketFactory getCurrentSocketFactory() {
        synchronized (monitor) {
            return socketFactory;
        }
    }

    /** Register a {@link ConfigServerApi} whose {@link SSLConnectionSocketFactory} will be kept up to date */
    public void registerConfigServerApi(ConfigServerApi configServerApi) {
        synchronized (monitor) {
            configServerApi.setSSLConnectionSocketFactory(socketFactory);
            configServerApis.add(configServerApi);
        }
    }

    public void unregisterConfigServerApi(ConfigServerApi configServerApi) {
        synchronized (monitor) {
            configServerApis.remove(configServerApi);
        }
    }

    @Override
    public void close() {
        if (sia != null) {
            sia.deconstruct();
        }
    }

    private SSLConnectionSocketFactory createSocketFactory(SSLContext sslContext) {
        return new SSLConnectionSocketFactory(sslContext, configServerHostnameVerifier);
    }

    private SSLConnectionSocketFactory createDefaultSslConnectionSocketFactory() {
        SSLContext sslContext = new SslContextBuilder().build();
        return createSocketFactory(sslContext);
    }

    private static HostnameVerifier createHostnameVerifier(AthenzIdentity identity) {
        return new AthenzIdentityVerifier(singleton(identity));
    }

}
