// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.athenz.zpe.AuthorizationResult;
import com.yahoo.vespa.athenz.zpe.Zpe;
import org.junit.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.MATCHED_CREDENTIAL_TYPE_ATTRIBUTE;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.MATCHED_ROLE_ATTRIBUTE;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.RESULT_ATTRIBUTE;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.SubjectAlternativeName.Type.RFC822_NAME;
import static com.yahoo.vespa.athenz.zpe.AuthorizationResult.Type;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzAuthorizationFilterTest {

    private static final AthenzResourceName RESOURCE_NAME = new AthenzResourceName("domain", "my-resource-name");
    private static final ZToken ROLE_TOKEN = new ZToken("v=Z1;d=domain;r=my-role;p=my-domain.my-service");
    private static final AthenzAccessToken ACCESS_TOKEN = new AthenzAccessToken(JWT.create().sign(Algorithm.none()));
    private static final AthenzIdentity IDENTITY = AthenzIdentities.from("user.john");
    private static final AthenzRole ROLE = new AthenzRole("my.domain", "my-role");
    private static final X509Certificate IDENTITY_CERTIFICATE = createDummyIdentityCertificate(IDENTITY);
    private static final X509Certificate ROLE_CERTIFICATE = createDummyRoleCertificate(ROLE, IDENTITY);
    private static final String ACTION = "update";
    private static final String HEADER_NAME = "Athenz-Role-Token";

    @Test
    public void accepts_request_with_access_token() {
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ACCESS_TOKEN);
        assertMatchedRole(request, ROLE);
    }

    @Test
    public void accepts_request_with_role_certificate() {
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, null, ROLE_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ROLE_CERTIFICATE);
        assertMatchedRole(request, ROLE);
    }

    @Test
    public void accepts_request_with_role_token() {
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(ROLE_TOKEN, null, null);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ROLE_TOKEN);
        assertMatchedRole(request, ROLE);
    }

    @Test
    public void returns_unauthorized_for_request_with_disabled_credential_type() {
        AthenzAuthorizationFilter filter =
                createFilter(new AllowingZpe(), List.of(EnabledCredentials.ROLE_CERTIFICATE, EnabledCredentials.ACCESS_TOKEN));

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(ROLE_TOKEN, null, null);
        filter.filter(request, responseHandler);

        assertStatusCode(responseHandler, 401);
        assertErrorMessage(responseHandler, "Not authorized - request did not contain any of the allowed credentials: " +
                "[Athenz X.509 role certificate, Athenz access token with X.509 identity certificate]");
    }

    @Test
    public void returns_forbidden_for_credentials_rejected_by_zpe() {
        AthenzAuthorizationFilter filter = createFilter(new DenyingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(ROLE_TOKEN, null, null);
        filter.filter(request, responseHandler);

        assertStatusCode(responseHandler, 403);
        assertAuthorizationResult(request, Type.DENY);
    }

    private static X509Certificate createDummyIdentityCertificate(AthenzIdentity identity) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal x500Name = new X500Principal("CN="+ identity.getFullName());
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(keyPair, x500Name, now, now.plus(Duration.ofDays(30)), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
    }

    private static X509Certificate createDummyRoleCertificate(AthenzRole role, AthenzIdentity identity) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal x500Name = new X500Principal("CN="+ role.domain().getName() + ":role." + role.roleName());
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(keyPair, x500Name, now, now.plus(Duration.ofDays(30)), SHA256_WITH_ECDSA, BigInteger.ONE)
                .addSubjectAlternativeName(new SubjectAlternativeName(RFC822_NAME, identity.getFullName() + "@my.domain.my-identity-provider"))
                .build();
    }

    private static DiscFilterRequest createRequest(ZToken roleToken, AthenzAccessToken accessToken, X509Certificate clientCert) {
        DiscFilterRequest request = Mockito.mock(DiscFilterRequest.class);
        when(request.getHeader(HEADER_NAME)).thenReturn(roleToken != null ? roleToken.getRawToken() : null);
        when(request.getHeader(AthenzAccessToken.HTTP_HEADER_NAME)).thenReturn(accessToken != null ? "Bearer " + accessToken.value() : null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/my/path");
        when(request.getQueryString()).thenReturn(null);
        when(request.getClientCertificateChain()).thenReturn(clientCert != null ? List.of(clientCert) : List.of());
        return request;
    }

    private static AthenzAuthorizationFilter createFilter(Zpe zpe, List<EnabledCredentials.Enum> enabledCredentials) {
        return new AthenzAuthorizationFilter(
                new AthenzAuthorizationFilterConfig(
                        new AthenzAuthorizationFilterConfig.Builder()
                                .roleTokenHeaderName(HEADER_NAME)
                                .enabledCredentials(enabledCredentials)),
                new StaticRequestResourceMapper(RESOURCE_NAME, ACTION),
                zpe);
    }

    private static void assertAuthorizationResult(DiscFilterRequest request, Type expectedResult) {
        verify(request).setAttribute(RESULT_ATTRIBUTE, expectedResult.name());
    }

    private static void assertStatusCode(MockResponseHandler responseHandler, int statusCode) {
        Response response = responseHandler.getResponse();
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), equalTo(statusCode));
    }

    private static void assertMatchedCredentialType(DiscFilterRequest request, EnabledCredentials.Enum expectedType) {
        verify(request).setAttribute(MATCHED_CREDENTIAL_TYPE_ATTRIBUTE, expectedType.name());
    }

    private static void assertRequestNotFiltered(MockResponseHandler responseHandler) {
        assertThat(responseHandler.getResponse(), nullValue());
    }

    private static void assertMatchedRole(DiscFilterRequest request, AthenzRole role) {
        verify(request).setAttribute(MATCHED_ROLE_ATTRIBUTE, role.roleName());
    }

    private static void assertErrorMessage(MockResponseHandler responseHandler, String errorMessage) {
        Response response = responseHandler.getResponse();
        assertThat(response, notNullValue());
        String content = responseHandler.readAll();
        assertThat(content, containsString(errorMessage));
    }

    private static class AllowingZpe implements Zpe {

        @Override
        public AuthorizationResult checkAccessAllowed(ZToken roleToken, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, ROLE);
        }

        @Override
        public AuthorizationResult checkAccessAllowed(X509Certificate roleCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, ROLE);
        }

        @Override
        public AuthorizationResult checkAccessAllowed(AthenzAccessToken accessToken, X509Certificate identityCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, ROLE);
        }
    }

    private static class DenyingZpe implements Zpe {
        @Override
        public AuthorizationResult checkAccessAllowed(ZToken roleToken, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.DENY);
        }

        @Override
        public AuthorizationResult checkAccessAllowed(X509Certificate roleCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.DENY);
        }

        @Override
        public AuthorizationResult checkAccessAllowed(AthenzAccessToken accessToken, X509Certificate identityCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.DENY);
        }
    }

}
