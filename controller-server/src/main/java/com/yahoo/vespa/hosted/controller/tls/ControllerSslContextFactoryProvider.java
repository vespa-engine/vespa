// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tls;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.http.ssl.impl.TlsContextBasedProvider;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.hosted.controller.tls.config.TlsConfig;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configures the controller's HTTPS connector with certificate and private key from a secret store.
 *
 * @author mpolden
 * @author bjorncs
 */
@SuppressWarnings("unused") // Injected
public class ControllerSslContextFactoryProvider extends TlsContextBasedProvider {

    private final KeyStore truststore;
    private final KeyStore keystore;
    private final Map<Integer, TlsContext> tlsContextMap = new ConcurrentHashMap<>();

    @Inject
    public ControllerSslContextFactoryProvider(SecretStore secretStore, TlsConfig config) {
        if (!Files.isReadable(Paths.get(config.caTrustStore()))) {
            throw new IllegalArgumentException("CA trust store file is not readable: " + config.caTrustStore());
        }
        // Trust store containing CA trust store from file
        this.truststore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .fromFile(Paths.get(config.caTrustStore()))
                .build();
        // Key store containing key pair from secret store
        this.keystore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(getClass().getSimpleName(), privateKey(secretStore, config), certificates(secretStore, config))
                .build();
    }

    @Override
    protected TlsContext getTlsContext(String containerId, int port) {
        return tlsContextMap.computeIfAbsent(port, this::createTlsContext);
    }

    private TlsContext createTlsContext(int port) {
        return new DefaultTlsContext(
                new SslContextBuilder()
                        .withKeyStore(keystore, new char[0])
                        .withTrustStore(truststore)
                        .build(),
                port != 443 ? PeerAuthentication.WANT : PeerAuthentication.DISABLED);
    }

    /** Get private key from secret store **/
    private static PrivateKey privateKey(SecretStore secretStore, TlsConfig config) {
        return KeyUtils.fromPemEncodedPrivateKey(secretStore.getSecret(config.privateKeySecret()));
    }

    /**
     * Get certificate from secret store. If certificate secret contains multiple certificates, e.g. intermediate
     * certificates, the entire chain will be read
     */
    private static List<X509Certificate> certificates(SecretStore secretStore, TlsConfig config) {
        return X509CertificateUtils.certificateListFromPem(secretStore.getSecret(config.certificateSecret()));
    }

}
