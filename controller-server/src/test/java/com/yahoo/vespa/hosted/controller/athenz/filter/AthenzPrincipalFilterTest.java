// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.InvalidTokenException;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzPrincipalFilterTest {

    private static final NToken NTOKEN = new NToken("dummy");
    private static final String ATHENZ_PRINCIPAL_HEADER = "Athenz-Principal-Auth";
    private static final AthenzIdentity IDENTITY = AthenzUser.fromUserId("bob");
    private static final X509Certificate CERTIFICATE = createSelfSignedCertificate(IDENTITY);
    private static final String ORIGIN = "http://localhost";
    private static final Set<String> CORS_ALLOWED_URLS = singleton(ORIGIN);

    private NTokenValidator validator;

    @Before
    public void before() {
        validator = mock(NTokenValidator.class);
    }

    @Test
    public void valid_ntoken_is_accepted() {
        DiscFilterRequest request = createRequestMock();
        AthenzPrincipal principal = new AthenzPrincipal(IDENTITY, NTOKEN);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getClientCertificateChain()).thenReturn(emptyList());
        when(validator.validate(NTOKEN)).thenReturn(principal);

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, new ResponseHandlerMock());

        verify(request).setUserPrincipal(principal);
    }

    private DiscFilterRequest createRequestMock() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader("Origin")).thenReturn(ORIGIN);
        return request;
    }

    @Test
    public void missing_token_and_certificate_is_unauthorized() {
        DiscFilterRequest request = createRequestMock();
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(null);
        when(request.getClientCertificateChain()).thenReturn(emptyList());

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, responseHandler);

        assertUnauthorized(responseHandler, "Unable to authenticate Athenz identity");
    }

    @Test
    public void invalid_token_is_unauthorized() {
        DiscFilterRequest request = createRequestMock();
        String errorMessage = "Invalid token";
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getClientCertificateChain()).thenReturn(emptyList());
        when(validator.validate(NTOKEN)).thenThrow(new InvalidTokenException(errorMessage));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, responseHandler);

        assertUnauthorized(responseHandler, errorMessage);
    }

    @Test
    public void certificate_is_accepted() {
        DiscFilterRequest request = createRequestMock();
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(null);
        when(request.getClientCertificateChain()).thenReturn(singletonList(CERTIFICATE));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, responseHandler);

        AthenzPrincipal expectedPrincipal = new AthenzPrincipal(IDENTITY);
        verify(request).setUserPrincipal(expectedPrincipal);
    }

    @Test
    public void both_ntoken_and_certificate_is_accepted() {
        DiscFilterRequest request = createRequestMock();
        AthenzPrincipal principalWithToken = new AthenzPrincipal(IDENTITY, NTOKEN);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getClientCertificateChain()).thenReturn(singletonList(CERTIFICATE));
        when(validator.validate(NTOKEN)).thenReturn(principalWithToken);

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, responseHandler);

        verify(request).setUserPrincipal(principalWithToken);
    }

    @Test
    public void conflicting_ntoken_and_certificate_is_unauthorized() {
        DiscFilterRequest request = createRequestMock();
        AthenzUser conflictingIdentity = AthenzUser.fromUserId("mallory");
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getClientCertificateChain())
                .thenReturn(singletonList(createSelfSignedCertificate(conflictingIdentity)));
        when(validator.validate(NTOKEN)).thenReturn(new AthenzPrincipal(IDENTITY));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(request, responseHandler);

        assertUnauthorized(responseHandler, "Identity in principal token does not match x509 CN");
    }

    private static void assertUnauthorized(ResponseHandlerMock responseHandler, String expectedMessageSubstring) {
        assertThat(responseHandler.response, notNullValue());
        assertThat(responseHandler.response.getStatus(), equalTo(UNAUTHORIZED));
        assertThat(responseHandler.getResponseContent(), containsString(expectedMessageSubstring));
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
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 512);
        X500Principal x500Name = new X500Principal("CN="+ identity.getFullName());
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(keyPair, x500Name, now, now.plus(Duration.ofDays(30)), SHA256_WITH_RSA, 1)
                .build();
    }

}
