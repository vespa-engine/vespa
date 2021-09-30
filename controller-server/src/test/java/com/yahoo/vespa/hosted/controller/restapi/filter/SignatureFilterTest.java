// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import ai.vespa.hosted.api.Method;
import ai.vespa.hosted.api.RequestSigner;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.restapi.ApplicationRequestToDiscFilterRequestWrapper;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SignatureFilterTest {

    private static final PublicKey publicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                                                "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                                                "-----END PUBLIC KEY-----\n");

    private static final PublicKey otherPublicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                     "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                                                     "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                                                     "-----END PUBLIC KEY-----\n");

    private static final PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey("-----BEGIN EC PRIVATE KEY-----\n" +
                                                                                   "MHcCAQEEIJUmbIX8YFLHtpRgkwqDDE3igU9RG6JD9cYHWAZii9j7oAoGCCqGSM49\n" +
                                                                                   "AwEHoUQDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9z/4jKSTHwbYR8wdsOSrJGVEU\n" +
                                                                                   "PbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                                                   "-----END EC PRIVATE KEY-----\n");

    private static final TenantAndApplicationId appId = TenantAndApplicationId.from("my-tenant", "my-app");
    private static final ApplicationId id = appId.defaultInstance();

    private ControllerTester tester;
    private ApplicationController applications;
    private SignatureFilter filter;
    private RequestSigner signer;

    @Before
    public void setup() {
        tester = new ControllerTester();
        applications = tester.controller().applications();
        filter = new SignatureFilter(tester.controller());
        signer = new RequestSigner(privateKey, id.serializedForm(), tester.clock());

        tester.curator().writeTenant(new CloudTenant(appId.tenant(),
                                                     Instant.EPOCH,
                                                     LastLoginInfo.EMPTY,
                                                     Optional.empty(),
                                                     ImmutableBiMap.of(),
                                                     TenantInfo.EMPTY,
                                                     List.of(),
                                                     Optional.empty()));
        tester.curator().writeApplication(new Application(appId, tester.clock().instant()));
    }

    @Test
    public void testFilter() {
        // Unsigned request gets no role.
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://host:123/path/./..//..%2F?query=empty&%3F=%26"));
        byte[] emptyBody = new byte[0];
        verifySecurityContext(requestOf(request.copy().method("GET", HttpRequest.BodyPublishers.ofByteArray(emptyBody)).build(), emptyBody),
                              null);

        // Signed request gets no role when no key is stored for the application.
        verifySecurityContext(requestOf(signer.signed(request.copy(), Method.GET, InputStream::nullInputStream), emptyBody),
                null);

        // Signed request gets no role when only non-matching keys are stored for the application.
        applications.lockApplicationOrThrow(appId, application -> applications.store(application.withDeployKey(otherPublicKey)));
        // Signed request gets no role when no key is stored for the application.
        verifySecurityContext(requestOf(signer.signed(request.copy(), Method.GET, InputStream::nullInputStream), emptyBody),
                              null);

        // Signed request gets a headless role when a matching key is stored for the application.
        applications.lockApplicationOrThrow(appId, application -> applications.store(application.withDeployKey(publicKey)));
        verifySecurityContext(requestOf(signer.signed(request.copy(), Method.GET, InputStream::nullInputStream), emptyBody),
                              new SecurityContext(new SimplePrincipal("headless@my-tenant.my-app"),
                                                  Set.of(Role.reader(id.tenant()),
                                                         Role.headless(id.tenant(), id.application())),
                                                  tester.clock().instant()));

        // Signed POST request with X-Key header gets a headless role.
        byte[] hiBytes = new byte[]{0x48, 0x69};
        verifySecurityContext(requestOf(signer.signed(request.copy(), Method.POST, () -> new ByteArrayInputStream(hiBytes)), hiBytes),
                              new SecurityContext(new SimplePrincipal("headless@my-tenant.my-app"),
                                                  Set.of(Role.reader(id.tenant()),
                                                         Role.headless(id.tenant(), id.application())),
                                                  tester.clock().instant()));

        // Signed request gets a developer role when a matching developer key is stored for the tenant.
        tester.curator().writeTenant(new CloudTenant(appId.tenant(),
                                                     Instant.EPOCH,
                                                     LastLoginInfo.EMPTY,
                                                     Optional.empty(),
                                                     ImmutableBiMap.of(publicKey, () -> "user"),
                                                     TenantInfo.EMPTY,
                                                     List.of(),
                                                     Optional.empty()));
        verifySecurityContext(requestOf(signer.signed(request.copy(), Method.POST, () -> new ByteArrayInputStream(hiBytes)), hiBytes),
                              new SecurityContext(new SimplePrincipal("user"),
                                                  Set.of(Role.reader(id.tenant()),
                                                         Role.developer(id.tenant())),
                                                  tester.clock().instant()));

        // Unsigned requests still get no roles.
        verifySecurityContext(requestOf(request.copy().method("GET", HttpRequest.BodyPublishers.ofByteArray(emptyBody)).build(), emptyBody),
                              null);
    }

    private void verifySecurityContext(DiscFilterRequest request, SecurityContext securityContext) {
        assertTrue(filter.filter(request).isEmpty());
        assertEquals(securityContext, request.getAttribute(SecurityContext.ATTRIBUTE_NAME));
    }

    private static DiscFilterRequest requestOf(HttpRequest request, byte[] body) {
        Request converted = new Request(request.uri().toString(), body, Request.Method.valueOf(request.method()));
        converted.getHeaders().addAll(request.headers().map());
        return new ApplicationRequestToDiscFilterRequestWrapper(converted);
    }

}
