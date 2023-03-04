// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.ErrorHandler;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.hosted.ca.CertificateTester;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author mpolden
 */
public class CertificateAuthorityApiTest extends ContainerTester {

    private static final String INSTANCE_ID = "1.cluster1.default.app1.tenant1.us-north-1.prod.node";
    private static final String INSTANCE_ID_WITH_SUFFIX = INSTANCE_ID + ".instanceid.athenz.dev-us-north-1.vespa.aws.oath.cloud";
    private static final String INVALID_INSTANCE_ID = "1.cluster1.default.otherapp.othertenant.us-north-1.prod.node";
    private static final String INVALID_INSTANCE_ID_WITH_SUFFIX = INVALID_INSTANCE_ID + ".instanceid.athenz.dev-us-north-1.vespa.aws.oath.cloud";

    private static final String CONTAINER_IDENTITY = "vespa.external.tenant";
    private static final String HOST_IDENTITY = "vespa.external.tenant-host";

    @BeforeEach
    public void before() {
        setCaCertificateAndKey();
    }

    @Test
    void register_instance() throws Exception {
        // POST instance registration
        var csr = CertificateTester.createCsr(List.of("node1.example.com", INSTANCE_ID_WITH_SUFFIX));
        assertIdentityResponse(new Request("http://localhost:12345/ca/v1/instance/",
                instanceRegistrationJson(csr),
                Request.Method.POST));

        // POST instance registration with ZTS client
        var ztsClient = new TestZtsClient(new AthenzPrincipal(new AthenzService(HOST_IDENTITY)), null, URI.create("http://localhost:12345/ca/v1/"), SSLContext.getDefault());
        var instanceIdentity = ztsClient.registerInstance(new AthenzService("vespa.external", "provider_prod_us-north-1"),
                new AthenzService(CONTAINER_IDENTITY),
                getAttestationData(),
                csr);
        assertEquals("CN=Vespa CA", instanceIdentity.certificate().getIssuerX500Principal().getName());
    }

    private X509Certificate registerInstance() throws Exception {
        // POST instance registration
        var csr = CertificateTester.createCsr(CONTAINER_IDENTITY, List.of("node1.example.com", INSTANCE_ID_WITH_SUFFIX));
        assertIdentityResponse(new Request("http://localhost:12345/ca/v1/instance/",
                                           instanceRegistrationJson(csr),
                                           Request.Method.POST));

        // POST instance registration with ZTS client
        var ztsClient = new TestZtsClient(new AthenzPrincipal(new AthenzService(HOST_IDENTITY)), null, URI.create("http://localhost:12345/ca/v1/"), SSLContext.getDefault());
        var instanceIdentity = ztsClient.registerInstance(new AthenzService("vespa.external", "provider_prod_us-north-1"),
                                                          new AthenzService(CONTAINER_IDENTITY),
                                                          getAttestationData(),
                                                          csr);
        return instanceIdentity.certificate();
    }

    @Test
    void refresh_instance() throws Exception {
        // Register instance to get cert
        var certificate = registerInstance();

        // POST instance refresh
        var principal = new AthenzPrincipal(new AthenzService(CONTAINER_IDENTITY));
        var csr = CertificateTester.createCsr(principal.getIdentity().getFullName(), List.of("node1.example.com", INSTANCE_ID_WITH_SUFFIX));
        var request = new Request("http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/" + INSTANCE_ID,
                instanceRefreshJson(csr),
                Request.Method.POST,
                principal);
        request.getAttributes().put(RequestUtils.JDISC_REQUEST_X509CERT, new X509Certificate[]{certificate});
        assertIdentityResponse(request);

        // POST instance refresh with ZTS client
        var ztsClient = new TestZtsClient(principal, certificate, URI.create("http://localhost:12345/ca/v1/"), SSLContext.getDefault());
        var instanceIdentity = ztsClient.refreshInstance(new AthenzService("vespa.external", "provider_prod_us-north-1"),
                new AthenzService(CONTAINER_IDENTITY),
                INSTANCE_ID,
                csr);
        assertEquals("CN=Vespa CA", instanceIdentity.certificate().getIssuerX500Principal().getName());
    }

    @Test
    void invalid_requests() throws Exception {
        // POST instance registration with missing fields
        assertResponse(400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"POST http://localhost:12345/ca/v1/instance/ failed: Missing required field 'provider'\"}",
                new Request("http://localhost:12345/ca/v1/instance/",
                        new byte[0],
                        Request.Method.POST));

        // POST instance registration without DNS name in CSR
        var csr = CertificateTester.createCsr();
        var request = new Request("http://localhost:12345/ca/v1/instance/",
                instanceRegistrationJson(csr),
                Request.Method.POST);
        assertResponse(400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"POST http://localhost:12345/ca/v1/instance/ failed: No instance ID found in CSR\"}", request);

        // POST instance refresh with missing field
        assertResponse(400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"POST http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/1.cluster1.default.app1.tenant1.us-north-1.prod.node failed: Missing required field 'csr'\"}",
                new Request("http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/" + INSTANCE_ID,
                        new byte[0],
                        Request.Method.POST));

        // POST instance refresh where instanceId does not match CSR dnsName
        var principal = new AthenzPrincipal(new AthenzService(CONTAINER_IDENTITY));
        var cert = CertificateTester.createCertificate(CONTAINER_IDENTITY, KeyUtils.generateKeypair(KeyAlgorithm.EC));
        csr = CertificateTester.createCsr(principal.getIdentity().getFullName(), List.of("node1.example.com", INSTANCE_ID_WITH_SUFFIX));
        request = new Request("http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/foobar",
                instanceRefreshJson(csr),
                Request.Method.POST,
                principal);
        request.getAttributes().put(RequestUtils.JDISC_REQUEST_X509CERT, new X509Certificate[]{cert});
        assertResponse(
                400,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"POST http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/foobar failed: Mismatch between instance ID in URL path and instance ID in CSR [instanceId=foobar,instanceIdFromCsr=1.cluster1.default.app1.tenant1.us-north-1.prod.node]\"}",
                request);

        // POST instance refresh using zts client where client cert does not contain instanceid
        var certificate = registerInstance();
        var ztsClient = new TestZtsClient(principal, certificate, URI.create("http://localhost:12345/ca/v1/"), SSLContext.getDefault());
        try {
            var invalidCsr = CertificateTester.createCsr(principal.getIdentity().getFullName(), List.of("node1.example.com", INVALID_INSTANCE_ID_WITH_SUFFIX));
            var instanceIdentity = ztsClient.refreshInstance(new AthenzService("vespa.external", "provider_prod_us-north-1"),
                    new AthenzService(CONTAINER_IDENTITY),
                    INSTANCE_ID,
                    invalidCsr);
            fail("Refresh instance should have failed");
        } catch (Exception e) {
            String expectedMessage = "Received error from ZTS: code=0, message=\"POST http://localhost:12345/ca/v1/instance/vespa.external.provider_prod_us-north-1/vespa.external/tenant/1.cluster1.default.app1.tenant1.us-north-1.prod.node failed: Mismatch between instance ID in URL path and instance ID in CSR [instanceId=1.cluster1.default.app1.tenant1.us-north-1.prod.node,instanceIdFromCsr=1.cluster1.default.otherapp.othertenant.us-north-1.prod.node]\"";
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private void setCaCertificateAndKey() {
        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        var caCertificatePem = X509CertificateUtils.toPem(CertificateTester.createCertificate("Vespa CA", keyPair));
        var privateKeyPem = KeyUtils.toPem(keyPair.getPrivate());
        secretStore().setSecret("vespa.external.ca.cert", caCertificatePem)
                     .setSecret("secretname", privateKeyPem);
    }

    private void assertIdentityResponse(Request request) {
        assertResponse(200, (body) -> {
            var slime = SlimeUtils.jsonToSlime(body);
            var root = slime.get();
            assertEquals("vespa.external.provider_prod_us-north-1", root.field("provider").asString());
            assertEquals("tenant", root.field("service").asString());
            assertEquals(INSTANCE_ID, root.field("instanceId").asString());
            var pemEncodedCertificate = root.field("x509Certificate").asString();
            assertTrue(pemEncodedCertificate.startsWith("-----BEGIN CERTIFICATE-----") &&
                       pemEncodedCertificate.endsWith("-----END CERTIFICATE-----\n"),
                       "Response contains PEM certificate");
        }, request);
    }

    private static byte[] instanceRefreshJson(Pkcs10Csr csr) {
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        var json  = "{\"csr\": \"" + csrPem + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] instanceRegistrationJson(Pkcs10Csr csr) {
        var csrPem = Pkcs10CsrUtils.toPem(csr);
        var json  = "{\n" +
               "  \"provider\": \"vespa.external.provider_prod_us-north-1\",\n" +
               "  \"domain\": \"vespa.external\",\n" +
               "  \"service\": \"tenant\",\n" +
               "  \"attestationData\": \""+getAttestationData()+"\",\n" +
               "  \"csr\": \"" + csrPem + "\"\n" +
               "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String getAttestationData () {
        var json = "{\n" +
                   "  \"signature\": \"SIGNATURE\",\n" +
                   "  \"signing-key-version\": 0,\n" +
                   "  \"provider-unique-id\": \"0.default.default.application.tenant.us-north-1.dev.tenant\",\n" +
                   "  \"provider-service\": \"domain.service\",\n" +
                   "  \"document-version\": 1,\n" +
                   "  \"configserver-hostname\": \"localhost\",\n" +
                   "  \"instance-hostname\": \"docker-container\",\n" +
                   "  \"created-at\": 1572000079.00000,\n" +
                   "  \"ip-addresses\": [\n" +
                   "    \"::1\"\n" +
                   "  ],\n" +
                   "  \"identity-type\": \"tenant\"\n" +
                   "}";
        return StringUtilities.escape(json);
    }

    /*
    Zts client that adds principal as header (since setting up ssl in test is cumbersome)
     */
    private static class TestZtsClient extends DefaultZtsClient {

        private final Principal principal;
        private final X509Certificate certificate;

        public TestZtsClient(Principal principal, X509Certificate certificate, URI ztsUrl, SSLContext sslContext) {
            super(ztsUrl, () -> sslContext, null, ErrorHandler.empty());
            this.principal = principal;
            this.certificate = certificate;
        }

        @Override
        protected <T> T execute(HttpUriRequest request, ResponseHandler<T> responseHandler) {
            request.addHeader("PRINCIPAL", principal.getName());
            Optional.ofNullable(certificate).ifPresent(cert -> {
                var pem = X509CertificateUtils.toPem(certificate);
                request.addHeader("CERTIFICATE", StringUtilities.escape(pem));
            });
            return super.execute(request, responseHandler);
        }
    }
}
