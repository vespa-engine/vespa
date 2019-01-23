// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.HostGlobPattern;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.RequiredPeerCredential.Field;
import com.yahoo.security.tls.policy.Role;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static com.yahoo.security.KeyAlgorithm.RSA;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_RSA;
import static com.yahoo.security.X509CertificateBuilder.generateRandomSerialNumber;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * @author bjorncs
 */
// TODO Use EC. Java/JSSE is currently unable to find compatible ciphers when using elliptic curve crypto from BouncyCastle
class CryptoUtils {

    static final KeyPair keyPair = KeyUtils.generateKeypair(RSA);

    static final X509Certificate certificate = X509CertificateBuilder
            .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, Instant.now().plus(1, DAYS), SHA256_WITH_RSA, generateRandomSerialNumber())
            .build();

    static final AuthorizedPeers authorizedPeers = new AuthorizedPeers(
            singleton(
                    new PeerPolicy(
                            "dummy-policy",
                            singleton(
                                    new Role("dummy-role")),
                            singletonList(
                                    new RequiredPeerCredential(
                                            Field.CN, new HostGlobPattern("dummy"))))));

    static TlsContext createTestTlsContext() {
        return new DefaultTlsContext(singletonList(certificate), keyPair.getPrivate(), singletonList(certificate), authorizedPeers, AuthorizationMode.ENFORCE, List.of());
    }

}
