// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.tenant;

import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OperatorCertificateSerializerTest {

    @Test
    public void testSerialization() {
        X509Certificate certificate = X509CertificateUtils.createSelfSigned("cn=mycn", Duration.ofDays(1)).certificate();
        Slime slime = OperatorCertificateSerializer.toSlime(List.of(certificate));
        List<X509Certificate> deserialized = OperatorCertificateSerializer.fromSlime(slime.get());
        assertEquals(1, deserialized.size());
        assertEquals(certificate, deserialized.get(0));
    }
}
