// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class TransportSecurityOptionsTest {

    private static final Path TEST_CONFIG_FILE = Paths.get("src/test/resources/transport-security-options.json");
    private static final TransportSecurityOptions OPTIONS = new TransportSecurityOptions.Builder()
            .withCertificates(Paths.get("certs.pem"), Paths.get("myhost.key"))
            .withCaCertificates(Paths.get("my_cas.pem"))
            .withAcceptedCiphers(Arrays.asList("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_AES_256_GCM_SHA384"))
            .withAcceptedProtocols(Collections.singletonList("TLSv1.2"))
            .withHostnameValidationDisabled(true)
            .build();

    @Test
    void can_read_options_from_json_file() {
        TransportSecurityOptions actualOptions =  TransportSecurityOptions.fromJsonFile(TEST_CONFIG_FILE);
        assertEquals(OPTIONS, actualOptions);
    }

    @Test
    void can_read_options_from_json() throws IOException {
        String tlsJson = new String(Files.readAllBytes(TEST_CONFIG_FILE), StandardCharsets.UTF_8);
        TransportSecurityOptions actualOptions = TransportSecurityOptions.fromJson(tlsJson);
        assertEquals(OPTIONS, actualOptions);
    }

}
