// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.Collections;
import java.util.Optional;

/**
 * @author hakon
 */
class SslConnectionSocketFactoryCreator {
    SSLConnectionSocketFactory createSocketFactory(
            ConfigServerInfo configServerInfo,
            Optional<KeyStoreOptions> keyStoreOptions) {
        SSLContext context = makeSslContext(configServerInfo, keyStoreOptions);
        return new SSLConnectionSocketFactory(context, makeHostnameVerifier(configServerInfo));
    }

    private static SSLContext makeSslContext(
            ConfigServerInfo configServerInfo,
            Optional<KeyStoreOptions> keyStoreOptions) {
        AthenzSslContextBuilder sslContextBuilder = new AthenzSslContextBuilder();
        configServerInfo.getTrustStoreOptions()
                .map(KeyStoreOptions::loadKeyStore)
                .ifPresent(sslContextBuilder::withTrustStore);
        keyStoreOptions.ifPresent(options ->
                sslContextBuilder.withKeyStore(options.loadKeyStore(), options.password));

        return sslContextBuilder.build();
    }

    private static HostnameVerifier makeHostnameVerifier(ConfigServerInfo configServerInfo) {
        return configServerInfo.getAthenzIdentity()
                .map(identity -> (HostnameVerifier) new AthenzIdentityVerifier(Collections.singleton(identity)))
                .orElseGet(SSLConnectionSocketFactory::getDefaultHostnameVerifier);
    }

}
