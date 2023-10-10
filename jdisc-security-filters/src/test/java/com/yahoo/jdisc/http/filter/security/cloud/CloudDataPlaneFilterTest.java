// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.container.jdisc.AclMapping.Action;
import com.yahoo.container.jdisc.HttpMethodAclMapping;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.jdisc.http.filter.util.FilterTestUtils;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static com.yahoo.jdisc.Response.Status.FORBIDDEN;
import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.jdisc.http.filter.security.cloud.Permission.READ;
import static com.yahoo.jdisc.http.filter.security.cloud.Permission.WRITE;
import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author bjorncs
 */
class CloudDataPlaneFilterTest {

    private static final X509Certificate FEED_CERT = certificate("my-feed-client");
    private static final X509Certificate SEARCH_CERT = certificate("my-search-client");
    private static final X509Certificate LEGACY_CLIENT_CERT = certificate("my-legacy-client");
    private static final String FEED_CLIENT_ID = "feed-client";
    private static final String MTLS_SEARCH_CLIENT_ID = "mtls-search-client";

    @Test
    void accepts_any_trusted_client_certificate_in_legacy_mode() {
        var req = FilterTestUtils.newRequestBuilder().withClientCertificate(LEGACY_CLIENT_CERT).build();
        var responseHandler = new MockResponseHandler();
        newFilterWithLegacyMode().filter(req, responseHandler);
        assertNull(responseHandler.getResponse());
        assertEquals(new ClientPrincipal(Set.of(), Set.of(READ, WRITE)), req.getUserPrincipal());
    }

    @Test
    void fails_on_missing_certificate_in_legacy_mode() {
        var req = FilterTestUtils.newRequestBuilder().build();
        var responseHandler = new MockResponseHandler();
        newFilterWithLegacyMode().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(UNAUTHORIZED, responseHandler.getResponse().getStatus());
    }

    @Test
    void accepts_client_with_valid_certificate() {
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.POST)
                .withClientCertificate(FEED_CERT)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNull(responseHandler.getResponse());
        assertEquals(new ClientPrincipal(Set.of(FEED_CLIENT_ID), Set.of(WRITE)), req.getUserPrincipal());
    }

    @Test
    void fails_on_client_with_invalid_permissions() {
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.POST)
                .withClientCertificate(SEARCH_CERT)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    @Test
    void supports_handler_with_custom_request_spec() {
        // Spec that maps POST as action 'read'
        var spec = RequestHandlerSpec.builder()
                .withAclMapping(HttpMethodAclMapping.standard()
                                        .override(Method.POST, Action.READ).build())
                .build();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.POST)
                .withClientCertificate(SEARCH_CERT)
                .withAttribute(RequestHandlerSpec.ATTRIBUTE_NAME, spec)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNull(responseHandler.getResponse());
        assertEquals(new ClientPrincipal(Set.of(MTLS_SEARCH_CLIENT_ID), Set.of(READ)), req.getUserPrincipal());
    }

    @Test
    void fails_on_handler_with_custom_request_spec_with_invalid_action() {
        // Spec that maps POST as action 'read'
        var spec = RequestHandlerSpec.builder()
                .withAclMapping(HttpMethodAclMapping.standard()
                                        .override(Method.GET, Action.custom("custom")).build())
                .build();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withClientCertificate(SEARCH_CERT)
                .withAttribute(RequestHandlerSpec.ATTRIBUTE_NAME, spec)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    private CloudDataPlaneFilter newFilterWithLegacyMode() {
        return new CloudDataPlaneFilter(new CloudDataPlaneFilterConfig.Builder().legacyMode(true).build());
    }

    private CloudDataPlaneFilter newFilterWithClientsConfig() {
        return new CloudDataPlaneFilter(
                new CloudDataPlaneFilterConfig.Builder()
                        .clients(List.of(
                                new CloudDataPlaneFilterConfig.Clients.Builder()
                                        .certificates(X509CertificateUtils.toPem(FEED_CERT))
                                        .permissions(WRITE.asString())
                                        .id(FEED_CLIENT_ID),
                                new CloudDataPlaneFilterConfig.Clients.Builder()
                                        .certificates(X509CertificateUtils.toPem(SEARCH_CERT))
                                        .permissions(READ.asString())
                                        .id(MTLS_SEARCH_CLIENT_ID)))
                        .build());
    }

    private static X509Certificate certificate(String name) {
        var key = KeyUtils.generateKeypair(EC);
        var subject = new X500Principal("CN=%s".formatted(name));
        return X509CertificateBuilder
                .fromKeypair(key, subject, EPOCH, EPOCH.plus(1, DAYS), SHA256_WITH_ECDSA, BigInteger.ONE).build();
    }


}
