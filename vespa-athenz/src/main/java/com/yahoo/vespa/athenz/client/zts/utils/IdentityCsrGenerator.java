// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.utils;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;

/**
 * Generates a {@link Pkcs10Csr} instance for use with {@link ZtsClient#getServiceIdentity(AthenzService, String, Pkcs10Csr)}
 *
 * @author bjorncs
 */
public class IdentityCsrGenerator {

    private final String dnsSuffix;

    public IdentityCsrGenerator(String dnsSuffix) {
        this.dnsSuffix = dnsSuffix;
    }

    public Pkcs10Csr generateIdentityCsr(AthenzService identity, KeyPair keypair) {
        return Pkcs10CsrBuilder.fromKeypair(new X500Principal("CN=" + identity.getFullName()), keypair, SHA256_WITH_RSA)
                .addSubjectAlternativeName(String.format(
                        "%s.%s.%s",
                        identity.getName(),
                        identity.getDomainName().replace(".", "-"),
                        dnsSuffix))
                .build();
    }

}
