// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.restapi.v2.Authorizer;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mpolden
 */
public class AuthorizationFilterTest {

    private AuthorizationFilter filter;

    @Before
    public void before() {
        filter = new AuthorizationFilter(new Authorizer(SystemName.main, new MockNodeRepository(new MockCurator(),
                                                                                                new MockNodeFlavors())),
                                         AuthorizationFilter::write);
    }

    @Test
    public void filter() {
        // These are just rudimentary tests of the filter. See AuthorizerTest for more exhaustive tests
        Optional<Response> response = invokeFilter(request(Request.Method.GET, "/"));
        assertResponse(401, "{\"error-code\":\"UNAUTHORIZED\",\"message\":\"GET / denied for " +
                            "unit-test: Missing credentials\"}", response);

        response = invokeFilter(request(Request.Method.GET, "/", "foo"));
        assertResponse(403, "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET / " +
                            "denied for unit-test: Invalid credentials\"}", response);

        response = invokeFilter(request(Request.Method.GET, "/nodes/v2/node/foo", "bar"));
        assertResponse(403, "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET /nodes/v2/node/foo " +
                            "denied for unit-test: Invalid credentials\"}", response);

        response = invokeFilter(request(Request.Method.GET, "/nodes/v2/node/foo", "foo"));
        assertSuccess(response);
    }

    private Optional<Response> invokeFilter(DiscFilterRequest request) {
        MockResponseHandler handler = new MockResponseHandler();
        filter.filter(request, handler);
        return Optional.ofNullable(handler.getResponse())
                       .map(response -> new Response(response.getStatus(), handler.readAll()));
    }

    private static DiscFilterRequest request(Request.Method method, String path) {
        return request(method, path, null);
    }

    private static DiscFilterRequest request(Request.Method method, String path, String commonName) {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getMethod()).thenReturn(method.name());
        when(request.getUri()).thenReturn(URI.create("http://localhost").resolve(path));
        when(request.getRemoteAddr()).thenReturn("unit-test");
        if (commonName != null) {
            X509Certificate cert = certificateFor(commonName, keyPair());
            when(request.getAttribute(ServletRequest.JDISC_REQUEST_X509CERT))
                    .thenReturn(new X509Certificate[]{cert});
        }
        return request;
    }

    private static void assertSuccess(Optional<Response> response) {
        assertFalse("No error in response", response.isPresent());
    }

    private static void assertResponse(int status, String body, Optional<Response> response) {
        assertTrue("Expected response from filter", response.isPresent());
        assertEquals("Response body", body, response.get().body);
        assertEquals("Status code", status, response.get().status);
    }

    /** Create a RSA public/private key pair */
    private static KeyPair keyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** Create a self signed certificate for commonName using given public/private key pair */
    private static X509Certificate certificateFor(String commonName, KeyPair keyPair) {
        try {
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                    .build(keyPair.getPrivate());
            X500Name x500Name = new X500Name("CN=" + commonName);
            Instant now = Instant.now();
            Date notBefore = Date.from(now);
            Date notAfter = Date.from(now.plus(Duration.ofDays(30)));
            X509v3CertificateBuilder certificateBuilder =
                    new JcaX509v3CertificateBuilder(
                            x500Name,
                            BigInteger.valueOf(now.toEpochMilli()), notBefore, notAfter, x500Name, keyPair.getPublic()
                    ).addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certificateBuilder.build(contentSigner));
        } catch (OperatorCreationException |IOException |CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Response {

        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

}
