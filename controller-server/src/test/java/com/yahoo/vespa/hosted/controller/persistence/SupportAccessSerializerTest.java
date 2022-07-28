// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccess;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccessGrant;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SupportAccessSerializerTest {

    private final X509Certificate cert_3_to_4 = grantCertificate(hour(3), hour(4));
    private final X509Certificate cert_7_to_19 = grantCertificate(hour(7), hour(19));

    SupportAccess supportAccessExample = SupportAccess.DISALLOWED_NO_HISTORY
            .withAllowedUntil(hour(24), "andreer", hour(2))
            .withGrant(new SupportAccessGrant("mortent", cert_3_to_4))
            .withGrant(new SupportAccessGrant("mortent", cert_7_to_19))
            .withDisallowed("andreer", hour(22))
            .withAllowedUntil(hour(36), "andreer", hour(30));

    @Language("JSON")
    private final String expectedWithCertificates = "{\n"
                                                    + "  \"history\": [\n"
                                                    + "    {\n"
                                                    + "      \"state\": \"allowed\",\n"
                                                    + "      \"at\": \"1970-01-02T06:00:00Z\",\n"
                                                    + "      \"until\": \"1970-01-02T12:00:00Z\",\n"
                                                    + "      \"by\": \"andreer\"\n"
                                                    + "    },\n"
                                                    + "    {\n"
                                                    + "      \"state\": \"disallowed\",\n"
                                                    + "      \"at\": \"1970-01-01T22:00:00Z\",\n"
                                                    + "      \"by\": \"andreer\"\n"
                                                    + "    },\n"
                                                    + "    {\n"
                                                    + "      \"state\": \"allowed\",\n"
                                                    + "      \"at\": \"1970-01-01T02:00:00Z\",\n"
                                                    + "      \"until\": \"1970-01-02T00:00:00Z\",\n"
                                                    + "      \"by\": \"andreer\"\n"
                                                    + "    }\n"
                                                    + "  ],\n"
                                                    + "  \"grants\": [\n"
                                                    + "    {\n"
                                                    + "      \"requestor\": \"mortent\",\n"
                                                    + "      \"certificate\": \"" + toPem(cert_7_to_19) + "\",\n"
                                                    + "      \"notBefore\": \"1970-01-01T07:00:00Z\",\n"
                                                    + "      \"notAfter\": \"1970-01-01T19:00:00Z\"\n"
                                                    + "    },\n"
                                                    + "    {\n"
                                                    + "      \"requestor\": \"mortent\",\n"
                                                    + "      \"certificate\": \"" + toPem(cert_3_to_4) + "\",\n"
                                                    + "      \"notBefore\": \"1970-01-01T03:00:00Z\",\n"
                                                    + "      \"notAfter\": \"1970-01-01T04:00:00Z\"\n"
                                                    + "    }\n"
                                                    + "  ]\n"
                                                    + "}\n";

    public String toPem(X509Certificate cert) {
        return X509CertificateUtils.toPem(cert).replace("\n", "\\n");
    }

    @Test
    void serialize_default() {
        var slime = SupportAccessSerializer.serializeCurrentState(SupportAccess.DISALLOWED_NO_HISTORY, Instant.EPOCH);
        assertSerialized(slime, "{\n" +
                "  \"state\": {\n" +
                "    \"supportAccess\": \"NOT_ALLOWED\"\n" +
                "  },\n" +
                "  \"history\": [ ],\n" +
                "  \"grants\": [ ]\n" +
                "}\n");
    }

    @Test
    void serialize_with_certificates() {
        var slime = SupportAccessSerializer.toSlime(supportAccessExample);
        assertSerialized(slime, expectedWithCertificates);
    }

    @Test
    void serialize_with_status() {
        var slime = SupportAccessSerializer.serializeCurrentState(supportAccessExample, hour(12));
        assertSerialized(slime,
                "{\n"
                        + "  \"state\": {\n"
                        + "    \"supportAccess\": \"ALLOWED\",\n"
                        + "    \"until\": \"1970-01-02T12:00:00Z\",\n"
                        + "    \"by\": \"andreer\"\n"
                        + "  },\n"
                        + "  \"history\": [\n"
                        + "    {\n"
                        + "      \"state\": \"allowed\",\n"
                        + "      \"at\": \"1970-01-02T06:00:00Z\",\n"
                        + "      \"until\": \"1970-01-02T12:00:00Z\",\n"
                        + "      \"by\": \"andreer\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"state\": \"disallowed\",\n"
                        + "      \"at\": \"1970-01-01T22:00:00Z\",\n"
                        + "      \"by\": \"andreer\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"state\": \"allowed\",\n"
                        + "      \"at\": \"1970-01-01T02:00:00Z\",\n"
                        + "      \"until\": \"1970-01-02T00:00:00Z\",\n"
                        + "      \"by\": \"andreer\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"state\": \"grant\",\n"
                        + "      \"at\": \"1970-01-01T03:00:00Z\",\n"
                        + "      \"until\": \"1970-01-01T04:00:00Z\",\n"
                        + "      \"by\": \"mortent\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"grants\": [\n"
                        + "    {\n"
                        + "      \"requestor\": \"mortent\",\n"
                        + "      \"notBefore\": \"1970-01-01T07:00:00Z\",\n"
                        + "      \"notAfter\": \"1970-01-01T19:00:00Z\"\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n");
    }

    @Test
    void deserialize() {
        var slime = SupportAccessSerializer.toSlime(supportAccessExample);
        assertSerialized(slime, expectedWithCertificates);

        var deserialized = SupportAccessSerializer.fromSlime(slime);
        assertEquals(supportAccessExample, deserialized);
    }

    private Instant hour(long h) {
        return Instant.EPOCH.plus(h, ChronoUnit.HOURS);
    }

    private void assertSerialized(Slime slime, String expected) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new JsonFormat(false).encode(out, slime);
            assertEquals(expected, out.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static X509Certificate grantCertificate(Instant notBefore, Instant notAfter) {
        return X509CertificateBuilder
                .fromKeypair(
                        KeyUtils.generateKeypair(KeyAlgorithm.EC, 256), new X500Principal("CN=mysubject"),
                        notBefore, notAfter, SignatureAlgorithm.SHA256_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
    }
}