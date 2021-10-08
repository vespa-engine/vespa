// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class ZipStreamReaderTest {

    @Test
    public void test_size_limit() {
        Map<String, String> entries = Map.of("foo.xml", "foobar");
        try {
            new ZipStreamReader(new ByteArrayInputStream(zip(entries)), "foo.xml"::equals, 1, true);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        entries = Map.of("foo.xml", "foobar",
                         "foo.jar", "0".repeat(100) // File not extracted and thus not subject to size limit
        );
        ZipStreamReader reader = new ZipStreamReader(new ByteArrayInputStream(zip(entries)), "foo.xml"::equals, 10, true);
        byte[] extracted = reader.entries().get(0).contentOrThrow();
        assertEquals("foobar", new String(extracted, StandardCharsets.UTF_8));
    }

    @Test
    public void test_paths() {
        Map<String, Boolean> tests = Map.of(
                "../../services.xml", true,
                "/../.././services.xml", true,
                "./application/././services.xml", true,
                "application//services.xml", true,
                "artifacts/", false, // empty dir
                "services..xml", false,
                "application/services.xml", false,
                "components/foo-bar-deploy.jar", false,
                "services.xml", false
        );
        tests.forEach((name, expectException) -> {
            try {
                new ZipStreamReader(new ByteArrayInputStream(zip(Map.of(name, "foo"))), name::equals, 1024, true);
                assertFalse("Expected exception for '" + name + "'", expectException);
            } catch (IllegalArgumentException ignored) {
                assertTrue("Unexpected exception for '" + name + "'", expectException);
            }
        });
    }

    @Test
    public void test_replacement() {
        ApplicationPackage applicationPackage = new ApplicationPackage(new byte[0]);
        List<X509Certificate> certificates = IntStream.range(0, 3)
                                                      .mapToObj(i -> {
                                                          KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
                                                          X500Principal subject = new X500Principal("CN=subject" + i);
                                                          return X509CertificateBuilder.fromKeypair(keyPair,
                                                                                                    subject,
                                                                                                    Instant.now(),
                                                                                                    Instant.now().plusSeconds(1),
                                                                                                    SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                                                                    BigInteger.valueOf(1))
                                                                                       .build();
                                                      })
                                                      .collect(Collectors.toUnmodifiableList());
        
        assertEquals(List.of(), applicationPackage.trustedCertificates());
        for (int i = 0; i < certificates.size(); i++) {
            applicationPackage = applicationPackage.withTrustedCertificate(certificates.get(i));
            assertEquals(certificates.subList(0, i + 1), applicationPackage.trustedCertificates());
        }
    }

    private static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return zip.toByteArray();
    }

}
