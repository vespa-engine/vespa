// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class CryptoUtilsTest {

    @Test
    public void certificate_signing_request_is_correct_and_can_be_serialized_to_pem() throws IOException {
        KeyPair keyPair = CryptoUtils.createKeyPair();
        PKCS10CertificationRequest csr = CryptoUtils.createCSR(
                "identity-domain", "identity-service", "vespa.cloud.com", "unique.instance.id", keyPair);
        String pem = CryptoUtils.toPem(csr);
        assertThat(pem, containsString("BEGIN CERTIFICATE REQUEST"));
        assertThat(pem, containsString("END CERTIFICATE REQUEST"));
    }

}
