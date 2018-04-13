// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresher;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresherFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Responsible for updating SSLConnectionSocketFactory on ConfigServerApiImpl asynchronously
 * and as required by embedded certificate expiry
 *
 * @author hakon
 */
public class SslConnectionSocketFactoryUpdater implements AutoCloseable {
    private final ConfigServerInfo configServerInfo;
    private final SslConnectionSocketFactoryCreator socketFactoryCreator;
    // Internal ConfigServerApi used to refresh the key store
    private final ConfigServerApiImpl configServerApi;
    private final Optional<ConfigServerKeyStoreRefresher> keyStoreRefresher;

    private final Object monitor = new Object();
    private SSLConnectionSocketFactory socketFactory = null;
    private final Set<ConfigServerApi> configServerApis = new HashSet<>();

    /**
     * Creates an updater with valid initial {@link SSLConnectionSocketFactory}
     *
     * @param hostname         the hostname of localhost
     * @throws RuntimeException if e.g. key store options have been specified, but was unable
     *                          create a create a key store with a valid certificate
     */
    public static SslConnectionSocketFactoryUpdater createAndRefreshKeyStoreIfNeeded(
            ConfigServerInfo configServerInfo, String hostname) {
        return new SslConnectionSocketFactoryUpdater(
                configServerInfo,
                hostname,
                ConfigServerKeyStoreRefresher::new,
                new SslConnectionSocketFactoryCreator());
    }

    /** Non-private for testing only */
    SslConnectionSocketFactoryUpdater(
            ConfigServerInfo configServerInfo,
            String hostname,
            ConfigServerKeyStoreRefresherFactory refresherFactory,
            SslConnectionSocketFactoryCreator socketFactoryCreator) {
        this.configServerInfo = configServerInfo;
        this.socketFactoryCreator = socketFactoryCreator;

        // The default socket factory is one without a keystore
        socketFactory = socketFactoryCreator.createSocketFactory(configServerInfo, Optional.empty());

        // ConfigServerApi used to refresh the key store. Does not itself rely on a socket
        // factory with key store, of course.
        configServerApi = ConfigServerApiImpl.createWithSocketFactory(configServerInfo, socketFactory);

        // If we have keystore options, we should make sure we use the keystore with the latest certificate,
        // start the keystore refresher.
        keyStoreRefresher = configServerInfo.getKeyStoreOptions().map(keyStoreOptions -> {
            ConfigServerKeyStoreRefresher keyStoreRefresher = refresherFactory.create(
                    keyStoreOptions,
                    this::updateSslConnectionSocketFactory,
                    configServerApi,
                    hostname);

            // Run the refresh once manually to make sure that we have a valid certificate, otherwise fail.
            try {
                keyStoreRefresher.refreshKeyStoreIfNeeded();
                updateSslConnectionSocketFactory();
            } catch (Exception e) {
                throw new RuntimeException("Failed to acquire certificate to config server", e);
            }

            keyStoreRefresher.start();
            return keyStoreRefresher;
        });
    }

    public SSLConnectionSocketFactory getCurrentSocketFactory() {
        return socketFactory;
    }

    /** Register a {@link ConfigServerApi} whose SSLConnectionSocketFactory will be kept up to date */
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
        keyStoreRefresher.ifPresent(ConfigServerKeyStoreRefresher::stop);
        configServerApi.close();
    }

    private void updateSslConnectionSocketFactory() {
        synchronized (monitor) {
            socketFactory = socketFactoryCreator.createSocketFactory(
                    configServerInfo,
                    configServerInfo.getKeyStoreOptions());

            configServerApis.forEach(api -> api.setSSLConnectionSocketFactory(socketFactory));
        }
    }
}
