// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.HostnameVerification;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.RequiredPeerCredential.Field;
import com.yahoo.security.tls.policy.Role;

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

/**
 * @author bjorncs
 */
class CryptoUtils {

    static final KeyPair keyPair = KeyUtils.generateKeypair(EC);

    static final X509Certificate certificate = X509CertificateBuilder
            .fromKeypair(keyPair, new X500Principal("CN=localhost"), EPOCH, Instant.now().plus(1, DAYS), SHA256_WITH_ECDSA, generateRandomSerialNumber())
            .build();

    static final AuthorizedPeers authorizedPeers = new AuthorizedPeers(
            singleton(
                    new PeerPolicy(
                            "localhost-policy",
                            singleton(
                                    new Role("localhost-role")),
                            singletonList(
                                    RequiredPeerCredential.of(Field.CN, "localhost")))));

    static TlsContext createTestTlsContext() {
        return new DefaultTlsContext(
                singletonList(certificate), keyPair.getPrivate(), singletonList(certificate), authorizedPeers,
                AuthorizationMode.ENFORCE, PeerAuthentication.NEED, HostnameVerification.ENABLED);
    }

}
