// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager.Mode;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManagersFactory;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.HostGlobPattern;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.RequiredPeerCredential.Field;
import com.yahoo.security.tls.policy.Role;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static com.yahoo.security.KeyAlgorithm.RSA;
import static com.yahoo.security.KeyStoreType.PKCS12;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_RSA;
import static com.yahoo.security.X509CertificateBuilder.generateRandomSerialNumber;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * @author bjorncs
 */
class CryptoUtils {
    static final SSLContext testSslContext = createTestSslContext();

    static TlsContext createTestTlsContext() {
        return new StaticTlsContext(testSslContext);
    }

    // TODO Use EC. Java/JSSE is currently unable to find compatible ciphers when using elliptic curve crypto from BouncyCastle
    static SSLContext createTestSslContext() {
        KeyPair keyPair = KeyUtils.generateKeypair(RSA);

        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, Instant.now().plus(1, DAYS), SHA256_WITH_RSA, generateRandomSerialNumber())
                .build();

        KeyStore trustStore = KeyStoreBuilder.withType(PKCS12)
                .withCertificateEntry("self-signed", certificate)
                .build();


        return new SslContextBuilder()
                .withTrustStore(trustStore)
                .withKeyStore(keyPair.getPrivate(), certificate)
                .withTrustManagerFactory(new PeerAuthorizerTrustManagersFactory(createAuthorizedPeers(), Mode.ENFORCE))
                .build();
    }

    private static AuthorizedPeers createAuthorizedPeers() {
        return new AuthorizedPeers(
                singleton(
                        new PeerPolicy(
                                "dummy-policy",
                                singleton(
                                        new Role("dummy-role")),
                                singletonList(
                                        new RequiredPeerCredential(
                                                Field.CN, new HostGlobPattern("dummy"))))));
    }

    private static class StaticTlsContext implements TlsContext {

        final SSLContext sslContext;

        StaticTlsContext(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public SSLEngine createSslEngine() {
            return sslContext.createSSLEngine();
        }

    }
}
