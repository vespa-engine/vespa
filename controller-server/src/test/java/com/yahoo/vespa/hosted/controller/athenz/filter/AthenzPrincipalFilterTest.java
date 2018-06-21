// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.application.container.handler.Request;
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
import com.yahoo.vespa.hosted.controller.restapi.ApplicationRequestToDiscFilterRequestWrapper;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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

    private NTokenValidatorMock validator;
    private ResponseHandlerMock responseHandler;

    @Before
    public void before() {
        this.validator = new NTokenValidatorMock();
        this.responseHandler = new ResponseHandlerMock();
    }

    @Test
    public void valid_ntoken_is_accepted() {
        Request request = defaultRequest();

        AthenzPrincipal principal = new AthenzPrincipal(IDENTITY, NTOKEN);
        validator.add(NTOKEN, principal);

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(request);
        filter.filter(filterRequest, new ResponseHandlerMock());

        assertEquals(principal, filterRequest.getUserPrincipal());
    }

    @Test
    public void missing_token_and_certificate_is_unauthorized() {
        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(new Request("/"));
        filter.filter(filterRequest, responseHandler);

        assertUnauthorized(responseHandler, "Unable to authenticate Athenz identity");
    }

    @Test
    public void invalid_token_is_unauthorized() {
        Request request = defaultRequest();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(request);
        filter.filter(filterRequest, responseHandler);

        String errorMessage = "Invalid token";
        assertUnauthorized(responseHandler, errorMessage);
    }

    @Test
    public void certificate_is_accepted() {
        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(new Request("/"), singletonList(CERTIFICATE));
        filter.filter(filterRequest, responseHandler);

        AthenzPrincipal expectedPrincipal = new AthenzPrincipal(IDENTITY);
        assertEquals(expectedPrincipal, filterRequest.getUserPrincipal());
    }

    @Test
    public void both_ntoken_and_certificate_is_accepted() {
        Request request = defaultRequest();

        AthenzPrincipal principalWithToken = new AthenzPrincipal(IDENTITY, NTOKEN);
        validator.add(NTOKEN, principalWithToken);

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(request, singletonList(CERTIFICATE));
        filter.filter(filterRequest, responseHandler);

        assertEquals(principalWithToken, filterRequest.getUserPrincipal());
    }

    @Test
    public void conflicting_ntoken_and_certificate_is_unauthorized() {
        Request request = defaultRequest();
        validator.add(NTOKEN, new AthenzPrincipal(IDENTITY));

        AthenzUser conflictingIdentity = AthenzUser.fromUserId("mallory");
        DiscFilterRequest filterRequest = new ApplicationRequestToDiscFilterRequestWrapper(request, singletonList(createSelfSignedCertificate(conflictingIdentity)));
        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER, CORS_ALLOWED_URLS);
        filter.filter(filterRequest, responseHandler);

        assertUnauthorized(responseHandler, "Identity in principal token does not match x509 CN");
    }

    private static Request defaultRequest() {
        Request request = new Request("/");
        request.getHeaders().add("Origin", ORIGIN);
        request.getHeaders().add(ATHENZ_PRINCIPAL_HEADER, NTOKEN.getRawToken());
        return request;
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

    private static class NTokenValidatorMock extends NTokenValidator {

        private final Map<NToken, AthenzPrincipal> validTokens = new HashMap<>();

        NTokenValidatorMock() {
            super((service, keyId) -> Optional.empty());
        }

        public NTokenValidatorMock add(NToken token, AthenzPrincipal principal) {
            validTokens.put(token, principal);
            return this;
        }

        @Override
        AthenzPrincipal validate(NToken token) throws InvalidTokenException {
            if (!validTokens.containsKey(token)) {
                throw new InvalidTokenException("Invalid token");
            }
            return validTokens.get(token);
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
