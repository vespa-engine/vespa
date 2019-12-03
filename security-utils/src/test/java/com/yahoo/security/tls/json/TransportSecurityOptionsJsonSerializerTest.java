// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.json;

import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.HostGlobPattern;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.Role;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.yahoo.security.tls.policy.RequiredPeerCredential.Field.CN;
import static com.yahoo.security.tls.policy.RequiredPeerCredential.Field.SAN_DNS;
import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class TransportSecurityOptionsJsonSerializerTest {

    @Rule public TemporaryFolder tempDirectory = new TemporaryFolder();

    private static final Path TEST_CONFIG_FILE = Paths.get("src/test/resources/transport-security-options.json");

    @Test
    public void can_serialize_and_deserialize_transport_security_options() {
        TransportSecurityOptions options = new TransportSecurityOptions.Builder()
                .withCaCertificates(Paths.get("/path/to/ca-certs.pem"))
                .withCertificates(Paths.get("/path/to/cert.pem"), Paths.get("/path/to/key.pem"))
                .withAuthorizedPeers(
                        new AuthorizedPeers(
                                new HashSet<>(Arrays.asList(
                                        new PeerPolicy("cfgserver", singleton(new Role("myrole")), Arrays.asList(
                                                new RequiredPeerCredential(CN, new HostGlobPattern("mycfgserver")),
                                                new RequiredPeerCredential(SAN_DNS, new HostGlobPattern("*.suffix.com")))),
                                        new PeerPolicy("node", singleton(new Role("anotherrole")), Collections.singletonList(new RequiredPeerCredential(CN, new HostGlobPattern("hostname"))))))))
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransportSecurityOptionsJsonSerializer serializer = new TransportSecurityOptionsJsonSerializer();
        serializer.serialize(out, options);
        TransportSecurityOptions deserializedOptions = serializer.deserialize(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(options, deserializedOptions);
    }

    @Test
    public void can_serialize_options_without_authorized_peers() throws IOException {
        TransportSecurityOptions options = new TransportSecurityOptions.Builder()
                .withCertificates(Paths.get("certs.pem"), Paths.get("myhost.key"))
                .withCaCertificates(Paths.get("my_cas.pem"))
                .withAcceptedCiphers(com.yahoo.vespa.jdk8compat.List.of("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" , "TLS_AES_256_GCM_SHA384"))
                .build();
        File outputFile = tempDirectory.newFile();
        try (OutputStream out = Files.newOutputStream(outputFile.toPath())) {
            new TransportSecurityOptionsJsonSerializer().serialize(out, options);
        }
        String expectedOutput = new String(Files.readAllBytes(TEST_CONFIG_FILE));
        String actualOutput = new String(Files.readAllBytes(outputFile.toPath()));
        assertJsonEquals(expectedOutput, actualOutput);
    }

}
