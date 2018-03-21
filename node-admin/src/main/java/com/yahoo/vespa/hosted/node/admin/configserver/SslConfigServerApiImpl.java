// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresher;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.security.Security;
import java.util.Collections;
import java.util.Optional;

/**
 * ConfigServerApi with proper keystore, truststore and hostname verifier to communicate with the
 * config server(s). The keystore is refreshed automatically.
 *
 * @author freva
 */
public class SslConfigServerApiImpl implements ConfigServerApi {

    private final ConfigServerApiImpl configServerApi;
    private final Environment environment;
    private final Optional<ConfigServerKeyStoreRefresher> keyStoreRefresher;

    public SslConfigServerApiImpl(Environment environment) {
        Security.addProvider(new BouncyCastleProvider());

        this.environment = environment;

        // At this point we don't know the state of the keystore, it may not exist at all, or the keystore
        // maybe exists, but the certificate in it is expired. Create the ConfigServerApi without a keystore
        // (but with truststore and hostname verifier).
        this.configServerApi = new ConfigServerApiImpl(
                environment.getConfigServerUris(), makeSslConnectionSocketFactory(Optional.empty()));

        // If we have keystore options, we should make sure we use the keystore with the latest certificate,
        // start the keystore refresher.
        this.keyStoreRefresher = environment.getKeyStoreOptions().map(keyStoreOptions -> {
            // Any callback from KeyStoreRefresher should result in using the latest keystore on disk
            Runnable connectionFactoryRefresher = () -> configServerApi.setSSLConnectionSocketFactory(
                    makeSslConnectionSocketFactory(Optional.of(keyStoreOptions)));

            ConfigServerKeyStoreRefresher keyStoreRefresher = new ConfigServerKeyStoreRefresher(
                    keyStoreOptions, connectionFactoryRefresher, configServerApi, environment.getParentHostHostname());

            // Run the refresh once manually to make sure that we have a valid certificate, otherwise fail.
            try {
                keyStoreRefresher.refreshKeyStoreIfNeeded();
                connectionFactoryRefresher.run(); // Update connectionFactory with the keystore on disk
            } catch (Exception e) {
                throw new RuntimeException("Failed to acquire certificate to config server", e);
            }

            keyStoreRefresher.start();
            return keyStoreRefresher;
        });
    }

    @Override
    public <T> T get(String path, Class<T> wantedReturnType) {
        return configServerApi.get(path, wantedReturnType);
    }

    @Override
    public <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return configServerApi.post(path, bodyJsonPojo, wantedReturnType);
    }

    @Override
    public <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return configServerApi.put(path, bodyJsonPojo, wantedReturnType);
    }

    @Override
    public <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return configServerApi.patch(path, bodyJsonPojo, wantedReturnType);
    }

    @Override
    public <T> T delete(String path, Class<T> wantedReturnType) {
        return configServerApi.delete(path, wantedReturnType);
    }

    @Override
    public void close() {
        keyStoreRefresher.ifPresent(ConfigServerKeyStoreRefresher::stop);
        configServerApi.close();
    }

    private SSLConnectionSocketFactory makeSslConnectionSocketFactory(Optional<KeyStoreOptions> keyStoreOptions) {
        return new SSLConnectionSocketFactory(makeSslContext(keyStoreOptions), makeHostnameVerifier());
    }

    private SSLContext makeSslContext(Optional<KeyStoreOptions> keyStoreOptions) {
        AthenzSslContextBuilder sslContextBuilder = new AthenzSslContextBuilder();
        environment.getTrustStoreOptions().map(KeyStoreOptions::loadKeyStore).ifPresent(sslContextBuilder::withTrustStore);
        keyStoreOptions.ifPresent(options -> sslContextBuilder.withKeyStore(options.loadKeyStore(), options.password));

        return sslContextBuilder.build();
    }

    private HostnameVerifier makeHostnameVerifier() {
        return environment.getAthenzIdentity()
                .map(identity -> (HostnameVerifier) new AthenzIdentityVerifier(Collections.singleton(identity)))
                .orElseGet(SSLConnectionSocketFactory::getDefaultHostnameVerifier);
    }
}
