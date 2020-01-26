// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.zpe.AuthorizationResult;
import com.yahoo.vespa.athenz.zpe.Zpe;
import org.junit.Test;
import org.mockito.Mockito;

import java.security.cert.X509Certificate;

import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.CredentialsToVerify.Enum.ANY;
import static com.yahoo.vespa.athenz.zpe.AuthorizationResult.*;
import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzAuthorizationFilterTest {

    private static final AthenzResourceName RESOURCE_NAME = new AthenzResourceName("domain", "my-resource-name");
    private static final String ACTION = "update";
    private static final String HEADER_NAME = "Athenz-Role-Token";
    private static final AthenzAuthorizationFilterConfig CONFIG = createConfig();

    private static AthenzAuthorizationFilterConfig createConfig() {
        return new AthenzAuthorizationFilterConfig(
                new AthenzAuthorizationFilterConfig.Builder()
                        .roleTokenHeaderName(HEADER_NAME)
                        .credentialsToVerify(ANY));
    }

    @Test
    public void accepts_valid_requests() {
        AthenzAuthorizationFilter filter =
                new AthenzAuthorizationFilter(
                        CONFIG, new StaticRequestResourceMapper(RESOURCE_NAME, ACTION), new AllowingZpe());

        RequestHandlerTestDriver.MockResponseHandler responseHandler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(createRequest(), responseHandler);

        assertNull(responseHandler.getResponse());
    }

    @Test
    public void returns_error_on_forbidden_requests() {
        AthenzAuthorizationFilter filter =
                new AthenzAuthorizationFilter(
                        CONFIG, new StaticRequestResourceMapper(RESOURCE_NAME, ACTION), new DenyingZpe());

        RequestHandlerTestDriver.MockResponseHandler responseHandler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(createRequest(), responseHandler);

        Response response = responseHandler.getResponse();
        assertNotNull(response);
        assertEquals(403, response.getStatus());
        String content = responseHandler.readAll();
        assertThat(content, containsString(Type.DENY.getDescription()));
    }

    private static DiscFilterRequest createRequest() {
        DiscFilterRequest request = Mockito.mock(DiscFilterRequest.class);
        when(request.getHeader(HEADER_NAME)).thenReturn("v=Z1;d=domain;r=my-role;p=my-domain.my-service");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/my/path");
        when(request.getQueryString()).thenReturn(null);
        when(request.getClientCertificateChain()).thenReturn(emptyList());
        return request;
    }

    static class AllowingZpe implements Zpe {
        @Override
        public AuthorizationResult checkAccessAllowed(ZToken roleToken, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, new AthenzRole(resourceName.getDomain(), "rolename"));
        }

        @Override
        public AuthorizationResult checkAccessAllowed(X509Certificate roleCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, new AthenzRole(resourceName.getDomain(), "rolename"));
        }

        @Override
        public AuthorizationResult checkAccessAllowed(AthenzAccessToken accessToken, X509Certificate identityCertificate, AthenzResourceName resourceName, String action) {
            return new AuthorizationResult(Type.ALLOW, new AthenzRole(resourceName.getDomain(), "rolename"));
        }
    }

    static class DenyingZpe implements Zpe {
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
