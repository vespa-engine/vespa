// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.ClusterType;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.hosted.ca.CertificateTester;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.ca.instance.InstanceRefresh;
import com.yahoo.vespa.hosted.ca.instance.InstanceRegistration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class InstanceSerializerTest {

    @Test
    void deserialize_instance_registration() {
        var csr = CertificateTester.createCsr();
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        SignedIdentityDocument signedIdentityDocument = new SignedIdentityDocument(
                "signature",
                0,
                new VespaUniqueInstanceId(0, "cluster", "instance", "application", "tenant", "region", "prod", IdentityType.NODE),
                new AthenzService("domain", "service"),
                0,
                "configserverhostname",
                "instancehostname",
                Instant.now().truncatedTo(ChronoUnit.MICROS),  // Truncate to the precision given from EntityBindingsMapper.toAttestationData()
                Collections.emptySet(),
                IdentityType.NODE,
                ClusterType.CONTAINER);

        var json = String.format("{\n" +
                "  \"provider\": \"provider_prod_us-north-1\",\n" +
                "  \"domain\": \"vespa.external\",\n" +
                "  \"service\": \"tenant\",\n" +
                "  \"attestationData\":\"%s\",\n" +
                "  \"csr\": \"" + csrPem + "\"\n" +
                "}", StringUtilities.escape(EntityBindingsMapper.toAttestationData(signedIdentityDocument)));
        var instanceRegistration = new InstanceRegistration("provider_prod_us-north-1", "vespa.external",
                "tenant", signedIdentityDocument,
                csr);
        var deserialized = InstanceSerializer.registrationFromSlime(SlimeUtils.jsonToSlime(json));
        assertEquals(instanceRegistration, deserialized);
    }

    @Test
    void serialize_instance_identity() {
        var certificate = CertificateTester.createCertificate();
        var pem = X509CertificateUtils.toPem(certificate);
        var identity = new InstanceIdentity("provider_prod_us-north-1", "tenant", "node1.example.com",
                Optional.of(certificate));
        var json = "{" +
                "\"provider\":\"provider_prod_us-north-1\"," +
                "\"service\":\"tenant\"," +
                "\"instanceId\":\"node1.example.com\"," +
                "\"x509Certificate\":\"" + pem.replace("\n", "\\n") + "\"" +
                "}";
        assertEquals(json, asJsonString(InstanceSerializer.identityToSlime(identity)));
    }

    @Test
    void serialize_instance_refresh() {
        var csr = CertificateTester.createCsr();
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        var json = "{\"csr\": \"" + csrPem + "\"}";
        var instanceRefresh = new InstanceRefresh(csr);
        var deserialized = InstanceSerializer.refreshFromSlime(SlimeUtils.jsonToSlime(json));
        assertEquals(instanceRefresh, deserialized);
    }

    private static String asJsonString(Slime slime) {
        try {
            return new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
