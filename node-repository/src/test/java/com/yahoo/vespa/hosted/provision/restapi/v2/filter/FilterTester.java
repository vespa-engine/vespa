// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.application.container.handler.Request.Method;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mpolden
 */
public class FilterTester {

    private final SecurityRequestFilter filter;

    public FilterTester(SecurityRequestFilter filter) {
        this.filter = filter;
    }

    public void assertSuccess(Request request) {
        assertFalse("No response written by filter", getResponse(request).isPresent());
    }

    public void assertRequest(Request request, int status, String body) {
        Optional<Response> response = getResponse(request);
        assertTrue("Expected response from filter", response.isPresent());
        assertEquals("Response body", body, response.get().body);
        assertEquals("Content type", "application/json",
                     response.get().headers.get("Content-Type").get(0));
        assertEquals("Status code", status, response.get().status);
    }

    private Optional<Response> getResponse(Request request) {
        RequestHandlerTestDriver.MockResponseHandler handler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(toDiscFilterRequest(request), handler);
        return Optional.ofNullable(handler.getResponse())
                       .map(response -> new Response(response.getStatus(), response.headers(), handler.readAll()));
    }

    private static DiscFilterRequest toDiscFilterRequest(Request request) {
        DiscFilterRequest r = mock(DiscFilterRequest.class);
        when(r.getMethod()).thenReturn(request.method().name());
        when(r.getUri()).thenReturn(URI.create("http://localhost").resolve(request.path()));
        when(r.getRemoteAddr()).thenReturn(request.remoteAddr());
        when(r.getLocalAddr()).thenReturn(request.localAddr());
        if (request.commonName().isPresent()) {
            X509Certificate cert = certificateFor(request.commonName().get(), keyPair());
            when(r.getClientCertificateChain()).thenReturn(Collections.singletonList(cert));
        }
        return r;
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
        private final Map<String, List<String>> headers;
        private final String body;

        private Response(int status, Map<String, List<String>> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

    }

    public static class Request {

        private final Method method;
        private final String path;
        private String localAddr;
        private String remoteAddr;
        private String commonName;

        public Request(Method method, String path) {
            this.method = method;
            this.path = path;
            this.commonName = null;
            this.localAddr = "local-addr";
            this.remoteAddr = "remote-addr";
        }

        public Method method() {
            return method;
        }

        public String path() {
            return path;
        }

        public String localAddr() {
            return localAddr;
        }

        public String remoteAddr() {
            return remoteAddr;
        }

        public Optional<String> commonName() {
            return Optional.ofNullable(commonName);
        }

        public Request commonName(String commonName) {
            this.commonName = commonName;
            return this;
        }

        public Request localAddr(String localAddr) {
            this.localAddr = localAddr;
            return this;
        }

        public Request remoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
            return this;
        }

    }

}
