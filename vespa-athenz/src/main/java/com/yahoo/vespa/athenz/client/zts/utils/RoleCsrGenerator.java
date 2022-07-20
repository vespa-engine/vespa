// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.utils;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.SubjectAlternativeName.Type;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_RSA;

/**
 * Generates a {@link Pkcs10Csr} instance for use with {@link ZtsClient#getRoleCertificate(AthenzRole, Pkcs10Csr)}.
 *
 * @author bjorncs
 */
public class RoleCsrGenerator {

    private final String dnsSuffix;

    public RoleCsrGenerator(String dnsSuffix) {
        this.dnsSuffix = dnsSuffix;
    }

    public Pkcs10Csr generateCsr(AthenzIdentity identity, AthenzRole role, KeyPair keyPair) {
        return Pkcs10CsrBuilder.fromKeypair(new X500Principal("CN=" + role.toResourceNameString()), keyPair, SHA256_WITH_RSA)
                .addSubjectAlternativeName(
                        Type.DNS,
                        String.format("%s.%s.%s", identity.getName(), identity.getDomainName().replace(".", "-"), dnsSuffix))
                .addSubjectAlternativeName(
                        Type.EMAIL,
                        String.format("%s@%s", identity.getFullName(), dnsSuffix))
                .build();
    }
}
