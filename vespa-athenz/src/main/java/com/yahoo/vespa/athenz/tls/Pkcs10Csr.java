// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * @author bjorncs
 */
public class Pkcs10Csr {

    private final PKCS10CertificationRequest csr;

    Pkcs10Csr(PKCS10CertificationRequest csr) {
        this.csr = csr;
    }

    PKCS10CertificationRequest getBcCsr() {
        return csr;
    }

    public X500Principal getSubject() {
        return new X500Principal(csr.getSubject().toString());
    }
}
