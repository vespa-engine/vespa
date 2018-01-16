// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.mock;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
import com.yahoo.vespa.athenz.api.AthenzRoleCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ZtsClientMock implements ZtsClient {
    private static final Logger log = Logger.getLogger(ZtsClientMock.class.getName());

    private final AthenzDbMock athenz;

    public ZtsClientMock(AthenzDbMock athenz) {
        this.athenz = athenz;
    }

    @Override
    public List<AthenzDomain> getTenantDomainsForUser(AthenzIdentity identity) {
        log.log(Level.INFO, "getTenantDomainsForUser(principal='%s')", identity);
        return athenz.domains.values().stream()
                .filter(domain -> domain.tenantAdmins.contains(identity) || domain.admins.contains(identity))
                .map(domain -> domain.name)
                .collect(toList());
    }

    @Override
    public AthenzIdentityCertificate getIdentityCertificate() {
        log.log(Level.INFO, "getIdentityCertificate()");
        try {
            KeyPair keyPair = createKeyPair();
            String subject = "CN=controller";
            return new AthenzIdentityCertificate(createCertificate(keyPair, subject), keyPair.getPrivate());
        } catch (NoSuchAlgorithmException | OperatorCreationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AthenzRoleCertificate getRoleCertificate(AthenzDomain roleDomain, String roleName) {
        log.log(Level.INFO,
                String.format("getRoleCertificate(roleDomain=%s, roleName=%s)", roleDomain.getName(), roleDomain));
        try {
            KeyPair keyPair = createKeyPair();
            String subject = String.format("CN=%s:role.%s", roleDomain.getName(), roleName);
            return new AthenzRoleCertificate(createCertificate(keyPair, subject), keyPair.getPrivate());
        } catch (NoSuchAlgorithmException | OperatorCreationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate createCertificate(KeyPair keyPair, String subject) throws
            OperatorCreationException, IOException {
        PKCS10CertificationRequest csr =
                Crypto.getPKCS10CertRequest(
                        Crypto.generateX509CSR(keyPair.getPrivate(), subject, null));
        return Crypto.generateX509Certificate(csr, keyPair.getPrivate(), new X500Name(subject), 3600, false);
    }

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        return keyGen.genKeyPair();
    }

}
