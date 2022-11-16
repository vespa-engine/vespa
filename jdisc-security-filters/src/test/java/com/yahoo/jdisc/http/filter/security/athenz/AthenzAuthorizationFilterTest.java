// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials;
import com.yahoo.jdisc.http.filter.util.FilterTestUtils;
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
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.MATCHED_CREDENTIAL_TYPE_ATTRIBUTE;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.MATCHED_ROLE_ATTRIBUTE;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter.RESULT_ATTRIBUTE;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.SubjectAlternativeName.Type.EMAIL;
import static com.yahoo.vespa.athenz.zpe.AuthorizationResult.Type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzAuthorizationFilterTest {

    private static final AthenzResourceName RESOURCE_NAME = new AthenzResourceName("domain", "my-resource-name");
    private static final ZToken ROLE_TOKEN = new ZToken("v=Z1;d=domain;r=my-role;p=my-domain.my-service");
    private static final AthenzAccessToken ACCESS_TOKEN = new AthenzAccessToken(JWT.create().sign(Algorithm.none()));
    private static final AthenzIdentity USER_IDENTITY = AthenzIdentities.from("user.john");
    private static final AthenzIdentity PROXY_IDENTITY = AthenzIdentities.from("proxy.service");
    private static final AthenzIdentity UNKNOWN_IDENTITY = AthenzIdentities.from("unknown.service");
    private static final AthenzRole ROLE = new AthenzRole("my.domain", "my-role");
    private static final X509Certificate USER_IDENTITY_CERTIFICATE = createDummyIdentityCertificate(USER_IDENTITY);
    private static final X509Certificate ROLE_CERTIFICATE = createDummyRoleCertificate(ROLE, USER_IDENTITY);
    private static final X509Certificate PROXY_IDENTITY_CERTIFICATE = createDummyIdentityCertificate(PROXY_IDENTITY);
    private static final X509Certificate UNKNOWN_IDENTITY_CERTIFICATE = createDummyIdentityCertificate(UNKNOWN_IDENTITY);
    private static final String ACTION = "update";
    private static final String HEADER_NAME = "Athenz-Role-Token";
    private static final String ACCEPTED_METRIC_NAME = "jdisc.http.filter.athenz.accepted_requests";
    private static final String REJECTED_METRIC_NAME = "jdisc.http.filter.athenz.rejected_requests";

    @Test
    void accepts_request_with_access_token() {
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, USER_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ACCESS_TOKEN);
        assertMatchedRole(request, ROLE);
    }

    @Test
    void accepts_request_with_role_certificate() {
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
    void accepts_request_with_role_token() {
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
    void accepts_request_with_proxied_access_token() {
        Zpe zpe = mock(Zpe.class);
        when(zpe.checkAccessAllowed(any(), any(), any(), any())).thenReturn(new AuthorizationResult(Type.ALLOW, ROLE));
        when(zpe.checkAccessAllowed((AthenzAccessToken) any(), any(), any())).thenReturn(new AuthorizationResult(Type.ALLOW, ROLE));
        AthenzAuthorizationFilter filter = createFilter(zpe, List.of(), new NullMetric(), PROXY_IDENTITY);

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, PROXY_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ACCESS_TOKEN);
        assertMatchedRole(request, ROLE);

        // Verify expected checkAccessAllowed overload is invoked
        verify(zpe, times(1)).checkAccessAllowed((AthenzAccessToken) any(), any(), any());
        verify(zpe, never()).checkAccessAllowed(any(), any(), any(), any());
    }

    @Test
    void accepts_request_with_access_token_and_matching_identity_certificate_with_proxy_support_enabled() {
        Zpe zpe = mock(Zpe.class);
        when(zpe.checkAccessAllowed(any(), any(), any(), any())).thenReturn(new AuthorizationResult(Type.ALLOW, ROLE));
        when(zpe.checkAccessAllowed((AthenzAccessToken) any(), any(), any())).thenReturn(new AuthorizationResult(Type.ALLOW, ROLE));
        AthenzAuthorizationFilter filter = createFilter(zpe, List.of(), new NullMetric(), PROXY_IDENTITY);

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, USER_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ACCESS_TOKEN);
        assertMatchedRole(request, ROLE);

        // Verify expected checkAccessAllowed overload is invoked
        verify(zpe, never()).checkAccessAllowed((AthenzAccessToken) any(), any(), any());
        verify(zpe, times(1)).checkAccessAllowed(any(), any(), any(), any());
    }

    @Test
    void returns_forbidden_when_identity_certificate_has_unknown_proxy_identity() {
        Zpe zpe = mock(Zpe.class);
        when(zpe.checkAccessAllowed(any(), any(), any(), any())).thenReturn(new AuthorizationResult(Type.DENY, ROLE));
        when(zpe.checkAccessAllowed((AthenzAccessToken) any(), any(), any())).thenReturn(new AuthorizationResult(Type.DENY, ROLE));
        AthenzAuthorizationFilter filter = createFilter(zpe, List.of(), new NullMetric(), PROXY_IDENTITY);

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, UNKNOWN_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.DENY);

        // Verify expected checkAccessAllowed overload is invoked
        verify(zpe, never()).checkAccessAllowed((AthenzAccessToken) any(), any(), any());
        verify(zpe, times(1)).checkAccessAllowed(any(), any(), any(), any());
    }

    @Test
    void returns_unauthorized_for_request_with_disabled_credential_type() {
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
    void returns_forbidden_for_credentials_rejected_by_zpe() {
        AthenzAuthorizationFilter filter = createFilter(new DenyingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(ROLE_TOKEN, null, null);
        filter.filter(request, responseHandler);

        assertStatusCode(responseHandler, 403);
        assertAuthorizationResult(request, Type.DENY);
    }

    @Test
    void reports_metrics_for_rejected_requests() {
        MetricMock metric = new MetricMock();
        AthenzAuthorizationFilter filter = createFilter(new DenyingZpe(), List.of(), metric, null);
        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, USER_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertMetrics(metric, REJECTED_METRIC_NAME, Map.of("zpe-status", "DENY", "status-code", "403"));
    }

    @Test
    void reports_metrics_for_accepted_requests() {
        MetricMock metric = new MetricMock();
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of(EnabledCredentials.ACCESS_TOKEN), metric, null);
        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, USER_IDENTITY_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertMetrics(metric, ACCEPTED_METRIC_NAME, Map.of("authz-required", "true"));
    }

    @Test
    void ignores_access_token_if_client_has_role_certificate() {
        AthenzAuthorizationFilter filter = createFilter(new AllowingZpe(), List.of());

        MockResponseHandler responseHandler = new MockResponseHandler();
        DiscFilterRequest request = createRequest(null, ACCESS_TOKEN, ROLE_CERTIFICATE);
        filter.filter(request, responseHandler);

        assertAuthorizationResult(request, Type.ALLOW);
        assertRequestNotFiltered(responseHandler);
        assertMatchedCredentialType(request, EnabledCredentials.ROLE_CERTIFICATE);
        assertMatchedRole(request, ROLE);
    }

    private void assertMetrics(MetricMock metric, String metricName, Map<String, String> dimensions) {
        assertTrue(metric.addInvocations.keySet().contains(metricName));
        SimpleMetricContext metricContext = metric.addInvocations.get(metricName);
        assertNotNull(metricName, "Metric not found " + metricName);
        for (Map.Entry<String, String> entry : dimensions.entrySet()) {
            String dimensionName = entry.getKey();
            String expected = entry.getValue();
            assertTrue(metricContext.dimensions.keySet().contains(dimensionName));
            assertEquals(expected, metricContext.dimensions.get(dimensionName));
        }
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
                .addSubjectAlternativeName(new SubjectAlternativeName(EMAIL, identity.getFullName() + "@my.domain.my-identity-provider"))
                .build();
    }

    private static DiscFilterRequest createRequest(ZToken roleToken, AthenzAccessToken accessToken, X509Certificate clientCert) {
        var builder = FilterTestUtils.newRequestBuilder().withUri("https://localhost/my/path");
        if (roleToken != null) builder.withHeader(HEADER_NAME, roleToken.getRawToken());
        if (accessToken != null) builder.withHeader(AthenzAccessToken.HTTP_HEADER_NAME, accessToken.value());
        if (clientCert != null) builder.withClientCertificate(clientCert);
        return builder.build();
    }

    private static AthenzAuthorizationFilter createFilter(Zpe zpe, List<EnabledCredentials.Enum> enabledCredentials) {
        return createFilter(zpe, enabledCredentials, new NullMetric(), null);
    }

    private static AthenzAuthorizationFilter createFilter(Zpe zpe, List<EnabledCredentials.Enum> enabledCredentials,
                                                          Metric metric, AthenzIdentity allowedProxyIdentity) {
        List<String> allowedProxyIdentities = allowedProxyIdentity != null ? List.of(allowedProxyIdentity.getFullName()) : List.of();
        return new AthenzAuthorizationFilter(
                new AthenzAuthorizationFilterConfig(
                        new AthenzAuthorizationFilterConfig.Builder()
                                .roleTokenHeaderName(HEADER_NAME)
                                .enabledCredentials(enabledCredentials)
                                .allowedProxyIdentities(allowedProxyIdentities)),
                new StaticRequestResourceMapper(RESOURCE_NAME, ACTION),
                zpe,
                metric,
                new AthenzRole("domain","reader"),
                new AthenzRole("domain", "writer"));
    }

    private static void assertAuthorizationResult(DiscFilterRequest request, Type expectedResult) {
        assertEquals(expectedResult.name(), request.getAttribute(RESULT_ATTRIBUTE));
    }

    private static void assertStatusCode(MockResponseHandler responseHandler, int statusCode) {
        Response response = responseHandler.getResponse();
        assertNotNull(response);
        assertEquals(statusCode, response.getStatus());
    }

    private static void assertMatchedCredentialType(DiscFilterRequest request, EnabledCredentials.Enum expectedType) {
        assertEquals(expectedType.name(), request.getAttribute(MATCHED_CREDENTIAL_TYPE_ATTRIBUTE));
    }

    private static void assertRequestNotFiltered(MockResponseHandler responseHandler) {
        assertNull(responseHandler.getResponse());
    }

    private static void assertMatchedRole(DiscFilterRequest request, AthenzRole role) {
        assertEquals(role.roleName(), request.getAttribute(MATCHED_ROLE_ATTRIBUTE));
    }

    private static void assertErrorMessage(MockResponseHandler responseHandler, String errorMessage) {
        Response response = responseHandler.getResponse();
        assertNotNull(response);
        String content = responseHandler.readAll();
        assertTrue(content.contains(errorMessage));
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

        @Override
        public AuthorizationResult checkAccessAllowed(AthenzAccessToken accessToken, AthenzResourceName resourceName, String action) {
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

        @Override
        public AuthorizationResult checkAccessAllowed(AthenzAccessToken accessToken, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.DENY);
        }
    }

    private static class MetricMock implements Metric {
        final ConcurrentHashMap<String, SimpleMetricContext> addInvocations = new ConcurrentHashMap<>();

        @Override public void add(String key, Number val, Context ctx) {
            addInvocations.put(key, (SimpleMetricContext)ctx);
        }
        @Override public void set(String key, Number val, Context ctx) {}
        @Override public Context createContext(Map<String, ?> properties) { return new SimpleMetricContext(properties); }
    }

    private static class SimpleMetricContext implements Metric.Context {
        final Map<String, String> dimensions;

        @SuppressWarnings("unchecked")
        SimpleMetricContext(Map<String, ?> dimensions) { this.dimensions = (Map<String, String>)dimensions; }
    }

}
