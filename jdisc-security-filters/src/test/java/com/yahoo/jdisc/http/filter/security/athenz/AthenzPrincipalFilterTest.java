// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzPrincipalFilterTest {

    private static final AthenzIdentity IDENTITY = AthenzUser.fromUserId("bob");
    private static final X509Certificate CERTIFICATE = createSelfSignedCertificate(IDENTITY);

    @Test
    public void missing_certificate_is_unauthorized() {
        DiscFilterRequest request = createRequestMock();
        when(request.getClientCertificateChain()).thenReturn(emptyList());

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = createFilter(false);
        filter.filter(request, responseHandler);

        assertUnauthorized(request, responseHandler, "Unable to authenticate Athenz identity");
    }

    @Test
    public void certificate_is_accepted() {
        DiscFilterRequest request = createRequestMock();
        when(request.getClientCertificateChain()).thenReturn(singletonList(CERTIFICATE));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = createFilter(false);
        filter.filter(request, responseHandler);

        AthenzPrincipal expectedPrincipal = new AthenzPrincipal(IDENTITY);
        assertAuthenticated(request, expectedPrincipal);
    }

    private void assertAuthenticated(DiscFilterRequest request, AthenzPrincipal expectedPrincipal) {
        verify(request).setUserPrincipal(expectedPrincipal);
        verify(request).setAttribute(AthenzPrincipalFilter.RESULT_PRINCIPAL, expectedPrincipal);
    }


    @Test
    public void no_response_produced_when_passthrough_mode_is_enabled() {
        DiscFilterRequest request = createRequestMock();
        when(request.getClientCertificateChain()).thenReturn(emptyList());

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = createFilter(true);
        filter.filter(request, responseHandler);

        assertThat(responseHandler.response, nullValue());
    }

    private DiscFilterRequest createRequestMock() {
        return mock(DiscFilterRequest.class);
    }

    private AthenzPrincipalFilter createFilter(boolean passthroughModeEnabled) {
        return new AthenzPrincipalFilter(passthroughModeEnabled);
    }

    private static void assertUnauthorized(DiscFilterRequest request, ResponseHandlerMock responseHandler, String expectedMessageSubstring) {
        assertThat(responseHandler.response, notNullValue());
        assertThat(responseHandler.response.getStatus(), equalTo(UNAUTHORIZED));
        assertThat(responseHandler.getResponseContent(), containsString(expectedMessageSubstring));
        verify(request).setAttribute(AthenzPrincipalFilter.RESULT_ERROR_CODE_ATTRIBUTE, UNAUTHORIZED);
    }

    private static class ResponseHandlerMock implements ResponseHandler {

        public Response response;
        public ReadableContentChannel contentChannel;

        @Override
        public ContentChannel handleResponse(Response r) {
            response = Objects.requireNonNull(r);
            contentChannel = new ReadableContentChannel();
            return contentChannel;
        }

        public String getResponseContent() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(contentChannel.toStream()))) {
                return br.lines().collect(joining(System.lineSeparator()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    private static X509Certificate createSelfSignedCertificate(AthenzIdentity identity) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal x500Name = new X500Principal("CN="+ identity.getFullName());
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(keyPair, x500Name, now, now.plus(Duration.ofDays(30)), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
    }

}
