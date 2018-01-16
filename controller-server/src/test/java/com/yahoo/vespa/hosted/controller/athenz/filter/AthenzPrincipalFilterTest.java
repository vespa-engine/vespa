// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.hosted.controller.api.integration.athenz.InvalidTokenException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
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

    private NTokenValidator validator;

    @Before
    public void before() {
        validator = mock(NTokenValidator.class);
    }

    @Test
    public void valid_ntoken_is_accepted() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        AthenzPrincipal principal = new AthenzPrincipal(IDENTITY, NTOKEN);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(validator.validate(NTOKEN)).thenReturn(principal);

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, new ResponseHandlerMock());

        verify(request).setUserPrincipal(principal);
    }

    @Test
    public void missing_token_and_certificate_is_unauthorized() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(null);

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        assertUnauthorized(responseHandler, "Unable to authenticate Athenz identity");
    }

    @Test
    public void invalid_token_is_unauthorized() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        String errorMessage = "Invalid token";
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(validator.validate(NTOKEN)).thenThrow(new InvalidTokenException(errorMessage));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        assertUnauthorized(responseHandler, errorMessage);
    }

    @Test
    public void certificate_is_accepted() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(null);
        when(request.getAttribute("jdisc.request.X509Certificate")).thenReturn(new X509Certificate[]{CERTIFICATE});

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        AthenzPrincipal expectedPrincipal = new AthenzPrincipal(IDENTITY);
        verify(request).setUserPrincipal(expectedPrincipal);
    }

    @Test
    public void both_ntoken_and_certificate_is_accepted() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        AthenzPrincipal principalWithToken = new AthenzPrincipal(IDENTITY, NTOKEN);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getAttribute("jdisc.request.X509Certificate")).thenReturn(new X509Certificate[]{CERTIFICATE});
        when(validator.validate(NTOKEN)).thenReturn(principalWithToken);

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        verify(request).setUserPrincipal(principalWithToken);
    }

    @Test
    public void conflicting_ntoken_and_certificate_is_unauthorized() {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        AthenzUser conflictingIdentity = AthenzUser.fromUserId("mallory");
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getRawToken());
        when(request.getAttribute("jdisc.request.X509Certificate"))
                .thenReturn(new X509Certificate[]{createSelfSignedCertificate(conflictingIdentity)});
        when(validator.validate(NTOKEN)).thenReturn(new AthenzPrincipal(IDENTITY));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
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

    // TODO Move this to separate athenz module/bundle
    private static X509Certificate createSelfSignedCertificate(AthenzIdentity identity) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(512);
            KeyPair keyPair = keyGen.genKeyPair();
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
            X500Name x500Name = new X500Name("CN="+ identity.getFullName());
            X509v3CertificateBuilder certificateBuilder =
                    new JcaX509v3CertificateBuilder(
                            x500Name, BigInteger.ONE, new Date(), Date.from(Instant.now().plus(Duration.ofDays(30))),
                            x500Name, keyPair.getPublic());
            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certificateBuilder.build(contentSigner));
        } catch (CertificateException | NoSuchAlgorithmException | OperatorCreationException e) {
            throw new RuntimeException(e);
        }
    }

}
