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
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Configures the controller's HTTPS connector with certificate and private key from a secret store.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class ControllerSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {

    private final SecretStore secretStore;
    private final TlsConfig config;
    private final SslContextFactory sslContextFactory;

    @Inject
    public ControllerSslContextFactoryProvider(SecretStore secretStore, TlsConfig config) {
        this.secretStore = Objects.requireNonNull(secretStore, "secretStore must be non-null");
        this.config = Objects.requireNonNull(config, "config must be non-null");
        this.sslContextFactory = create();
    }

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        return sslContextFactory;
    }

    /** Create a SslContextFactory backed by an in-memory key and trust store */
    private SslContextFactory create() {
        if (!Files.isReadable(Paths.get(config.caTrustStore()))) {
            throw new IllegalArgumentException("CA trust store file is not readable: " + config.caTrustStore());
        }
        SslContextFactory factory = new SslContextFactory();

        // Do not exclude TLS_RSA_* ciphers
        String[] excludedCiphers = Arrays.stream(factory.getExcludeCipherSuites())
                                         .filter(cipherPattern -> !cipherPattern.equals("^TLS_RSA_.*$"))
                                         .toArray(String[]::new);
        factory.setExcludeCipherSuites(excludedCiphers);
        factory.setWantClientAuth(true);

        // Trust store containing CA trust store from file
        factory.setTrustStore(KeyStoreBuilder.withType(KeyStoreType.JKS)
                                             .fromFile(Paths.get(config.caTrustStore()))
                                             .build());

        // Key store containing key pair from secret store
        factory.setKeyStore(KeyStoreBuilder.withType(KeyStoreType.JKS)
                                           .withKeyEntry(getClass().getSimpleName(), privateKey(), certificate())
                                           .build());

        factory.setKeyStorePassword("");
        return factory;
    }

    /** Get private key from secret store */
    private PrivateKey privateKey() {
        return KeyUtils.fromPemEncodedPrivateKey(secretStore.getSecret(config.privateKeySecret()));
    }

    /** Get certificate from secret store */
    private List<X509Certificate> certificate() {
        return X509CertificateUtils.certificateListFromPem(secretStore.getSecret(config.certificateSecret()));
    }

}
