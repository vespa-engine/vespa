// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tls;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.hosted.controller.tls.config.TlsConfig;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
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
public class ControllerSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {

    private final KeyStore truststore;
    private final KeyStore keystore;
    private final Map<Integer, SslContextFactory> sslContextFactories = new ConcurrentHashMap<>();

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
    public SslContextFactory getInstance(String containerId, int port) {
        return sslContextFactories.computeIfAbsent(port, this::createSslContextFactory);
    }

    /** Create a SslContextFactory backed by an in-memory key and trust store */
    private SslContextFactory createSslContextFactory(int port) {
        SslContextFactory factory = new SslContextFactory();
        // TODO Remove cipher exclusions on Vespa 7 (require ciphers with forward secrecy)
        // Do not exclude TLS_RSA_* ciphers
        String[] excludedCiphers = Arrays.stream(factory.getExcludeCipherSuites())
                                         .filter(cipherPattern -> !cipherPattern.equals("^TLS_RSA_.*$"))
                                         .toArray(String[]::new);
        factory.setExcludeCipherSuites(excludedCiphers);
        if (port != 443) {
            factory.setWantClientAuth(true);
        }
        factory.setTrustStore(truststore);
        factory.setKeyStore(keystore);
        factory.setKeyStorePassword("");
        return factory;
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
