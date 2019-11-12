// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.ca.CertificateTester;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.ca.instance.InstanceRefresh;
import com.yahoo.vespa.hosted.ca.instance.InstanceRegistration;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class InstanceSerializerTest {

    @Test
    public void deserialize_instance_registration() {
        var csr = CertificateTester.createCsr();
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        var json = "{\n" +
                   "  \"provider\": \"provider_prod_us-north-1\",\n" +
                   "  \"domain\": \"vespa.external\",\n" +
                   "  \"service\": \"tenant\",\n" +
                   "  \"attestationData\": \"identity document from configserevr\",\n" +
                   "  \"csr\": \"" + csrPem + "\"\n" +
                   "}";
        var instanceRegistration = new InstanceRegistration("provider_prod_us-north-1", "vespa.external",
                                                            "tenant", "identity document from configserevr",
                                                            csr);
        var deserialized = InstanceSerializer.registrationFromSlime(SlimeUtils.jsonToSlime(json));
        assertEquals(instanceRegistration, deserialized);
    }

    @Test
    public void serialize_instance_identity() {
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
    public void serialize_instance_refresh() {
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
