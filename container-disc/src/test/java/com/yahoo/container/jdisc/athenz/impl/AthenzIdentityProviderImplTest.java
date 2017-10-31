// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 */
public class AthenzIdentityProviderImplTest {

    @Test
    public void ntoken_fetched_on_init() throws IOException {
        IdentityConfig config = new IdentityConfig(new IdentityConfig.Builder().service("tenantService").domain("tenantDomain").loadBalancerAddress("cfg"));
        ServiceProviderApi serviceProviderApi = mock(ServiceProviderApi.class);
        AthenzService athenzService = mock(AthenzService.class);

        when(serviceProviderApi.getSignedIdentityDocument()).thenReturn(getIdentityDocument());
        when(athenzService.sendInstanceRegisterRequest(any(), anyString())).thenReturn(
                new InstanceIdentity(null, null, null, null, null, null, null, null, "TOKEN"));

        AthenzIdentityProvider identityProvider = new AthenzIdentityProviderImpl(config, serviceProviderApi, athenzService);

        Assert.assertEquals("TOKEN", identityProvider.getNToken());
    }

    private String getIdentityDocument() {
        return "{\n" +
               "  \"identity-document\": \"eyJwcm92aWRlci11bmlxdWUtaWQiOnsidGVuYW50IjoidGVuYW50IiwiYXBwbGljYXRpb24iOiJhcHBsaWNhdGlvbiIsImVudmlyb25tZW50IjoiZGV2IiwicmVnaW9uIjoidXMtbm9ydGgtMSIsImluc3RhbmNlIjoiZGVmYXVsdCIsImNsdXN0ZXItaWQiOiJkZWZhdWx0IiwiY2x1c3Rlci1pbmRleCI6MH0sImNvbmZpZ3NlcnZlci1ob3N0bmFtZSI6ImxvY2FsaG9zdCIsImluc3RhbmNlLWhvc3RuYW1lIjoieC55LmNvbSIsImNyZWF0ZWQtYXQiOjE1MDg3NDgyODUuNzQyMDAwMDAwfQ==\",\n" +
               "  \"signature\": \"kkEJB/98cy1FeXxzSjtvGH2a6BFgZu/9/kzCcAqRMZjENxnw5jyO1/bjZVzw2Sz4YHPsWSx2uxb32hiQ0U8rMP0zfA9nERIalSP0jB/hMU8laezGhdpk6VKZPJRC6YKAB9Bsv2qUIfMsSxkMqf66GUvjZAGaYsnNa2yHc1jIYHOGMeJO+HNPYJjGv26xPfAOPIKQzs3RmKrc3FoweTCsIwm5oblqekdJvVWYe0obwlOSB5uwc1zpq3Ie1QBFtJRuCGMVHg1pDPxXKBHLClGIrEvzLmICy6IRdHszSO5qiwujUD7sbrbM0sB/u0cYucxbcsGRUmBvme3UAw2mW9POVQ==\",\n" +
               "  \"signing-key-version\": 0,\n" +
               "  \"provider-unique-id\": \"tenant.application.dev.us-north-1.default.default.0\",\n" +
               "  \"dns-suffix\": \"dnsSuffix\",\n" +
               "  \"provider-service\": \"service\",\n" +
               "  \"zts-endpoint\": \"localhost/zts\", \n" +
               "  \"document-version\": 1\n" +
               "}";

    }
}
