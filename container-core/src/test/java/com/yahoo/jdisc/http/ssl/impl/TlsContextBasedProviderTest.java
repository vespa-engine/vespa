// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.AuthorizedPeers;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.HostnameVerification;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author bjorncs
 */
public class TlsContextBasedProviderTest {

    @Test
    void creates_sslcontextfactory_from_tlscontext() {
        TlsContext tlsContext = createTlsContext();
        var provider = new SimpleTlsContextBasedProvider(tlsContext);
        DefaultConnectorSsl ssl = new DefaultConnectorSsl();
        provider.configureSsl(ssl, "dummyContainerId", 8080);
        assertArrayEquals(tlsContext.parameters().getCipherSuites(), ssl.createSslContextFactory().getIncludeCipherSuites());
    }

    private static TlsContext createTlsContext() {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(
                        keyPair,
                        new X500Principal("CN=dummy"),
                        Instant.EPOCH,
                        Instant.EPOCH.plus(100000, ChronoUnit.DAYS),
                        SHA256_WITH_ECDSA,
                        BigInteger.ONE)
                .build();
        return new DefaultTlsContext(
                List.of(certificate), keyPair.getPrivate(), List.of(certificate), new AuthorizedPeers(Set.of()), AuthorizationMode.ENFORCE, PeerAuthentication.NEED, HostnameVerification.ENABLED);
    }

    private static class SimpleTlsContextBasedProvider extends TlsContextBasedProvider {
        final TlsContext tlsContext;

        SimpleTlsContextBasedProvider(TlsContext tlsContext) {
            this.tlsContext = tlsContext;
        }

        @Override
        protected TlsContext getTlsContext(String containerId, int port) {
            return tlsContext;
        }

    }
}