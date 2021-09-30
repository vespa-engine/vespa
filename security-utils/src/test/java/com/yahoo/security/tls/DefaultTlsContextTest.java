// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.Role;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.X509CertificateBuilder.generateRandomSerialNumber;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class DefaultTlsContextTest {

    @Test
    public void can_create_sslcontext_from_credentials() {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);

        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, Instant.now().plus(1, DAYS), SHA256_WITH_ECDSA, generateRandomSerialNumber())
                .build();

        AuthorizedPeers authorizedPeers = new AuthorizedPeers(
                singleton(
                        new PeerPolicy(
                                "dummy-policy",
                                singleton(new Role("dummy-role")),
                                singletonList(RequiredPeerCredential.of(RequiredPeerCredential.Field.CN, "dummy")))));

        DefaultTlsContext tlsContext =
                new DefaultTlsContext(
                        singletonList(certificate), keyPair.getPrivate(), singletonList(certificate), authorizedPeers,
                        AuthorizationMode.ENFORCE, PeerAuthentication.NEED, HostnameVerification.ENABLED);

        SSLEngine sslEngine = tlsContext.createSslEngine();
        assertThat(sslEngine).isNotNull();
        String[] enabledCiphers = sslEngine.getEnabledCipherSuites();
        assertThat(enabledCiphers).isNotEmpty();
        assertThat(enabledCiphers).isSubsetOf(TlsContext.ALLOWED_CIPHER_SUITES.toArray(new String[0]));

        String[] enabledProtocols = sslEngine.getEnabledProtocols();
        assertThat(enabledProtocols).contains("TLSv1.2");
    }

}