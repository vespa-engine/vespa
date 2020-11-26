// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.filter.security.misc;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class VespaTlsFilterTest {

    @Test
    public void testFilter() {
        assertSuccess(createRequest(List.of(createCertificate())));
        assertForbidden(createRequest(Collections.emptyList()));
    }

    private static X509Certificate createCertificate() {
        return X509CertificateBuilder
                .fromKeypair(
                        KeyUtils.generateKeypair(KeyAlgorithm.EC), new X500Principal("CN=test"),
                        Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS),
                        SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
    }

    private static DiscFilterRequest createRequest(List<X509Certificate> certChain) {
        DiscFilterRequest request = Mockito.mock(DiscFilterRequest.class);
        when(request.getClientCertificateChain()).thenReturn(certChain);
        when(request.getMethod()).thenReturn("GET");
        when(request.getUri()).thenReturn(URI.create("http://localhost:8080/"));
        return request;
    }

    private static void assertForbidden(DiscFilterRequest request) {
        VespaTlsFilter filter = new VespaTlsFilter();
        RequestHandlerTestDriver.MockResponseHandler handler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(request, handler);
        assertEquals(Response.Status.FORBIDDEN, handler.getStatus());
    }

    private static void assertSuccess(DiscFilterRequest request) {
        VespaTlsFilter filter = new VespaTlsFilter();
        RequestHandlerTestDriver.MockResponseHandler handler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(request, handler);
        assertNull(handler.getResponse());
    }
}
