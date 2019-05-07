// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import ai.vespa.hosted.api.Method;
import ai.vespa.hosted.api.RequestSigner;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.restapi.ApplicationRequestToDiscFilterRequestWrapper;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SignatureFilterTest {

    private static final String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                 "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                 "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                 "-----END PUBLIC KEY-----\n";

    private static final String otherPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                 "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                 "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                 "-----END PUBLIC KEY-----\n";

    private static final String privateKey = "-----BEGIN EC PRIVATE KEY-----\n" +
                                                  "MHcCAQEEIJUmbIX8YFLHtpRgkwqDDE3igU9RG6JD9cYHWAZii9j7oAoGCCqGSM49\n" +
                                                  "AwEHoUQDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9z/4jKSTHwbYR8wdsOSrJGVEU\n" +
                                                  "PbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                  "-----END EC PRIVATE KEY-----\n";

    private static final ApplicationId id = ApplicationId.from("my-tenant", "my-app", "default");

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

        tester.createApplication(tester.createTenant(id.tenant().value(), "unused", 496L),
                                 id.application().value(),
                                 id.instance().value(),
                                 28L);
    }

    @Test
    public void testFilter() {
        // Unsigned request gets no role.
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://host:123/path/./..//..%2F?query=empty&%3F=%26"));
        byte[] emptyBody = new byte[0];
        DiscFilterRequest unsigned = requestOf(request.method("GET", HttpRequest.BodyPublishers.ofByteArray(emptyBody)).build(), emptyBody);
        filter.filter(unsigned);
        assertNull(unsigned.getAttribute(SecurityContext.ATTRIBUTE_NAME));

        // Signed request gets no role when no key is stored for the application.
        DiscFilterRequest signed = requestOf(signer.signed(request, Method.GET, InputStream::nullInputStream), emptyBody);
        filter.filter(signed);
        assertNull(signed.getAttribute(SecurityContext.ATTRIBUTE_NAME));

        // Signed request gets no role when a non-matching key is stored for the application.
        applications.lockOrThrow(id, application -> applications.store(application.withPemDeployKey(otherPublicKey)));
        filter.filter(signed);
        assertNull(signed.getAttribute(SecurityContext.ATTRIBUTE_NAME));

        // Signed request gets a build service role when a matching key is stored for the application.
        applications.lockOrThrow(id, application -> applications.store(application.withPemDeployKey(publicKey)));
        assertTrue(filter.filter(signed).isEmpty());
        SecurityContext securityContext = (SecurityContext) signed.getAttribute(SecurityContext.ATTRIBUTE_NAME);
        assertEquals("buildService@my-tenant.my-app", securityContext.principal().getName());
        assertEquals(Set.of(Role.buildService(id.tenant(), id.application()),
                            Role.applicationDeveloper(id.tenant(), id.application())),
                     securityContext.roles());

        // Signed POST request also gets a build service role.
        byte[] hiBytes = new byte[]{0x48, 0x69};
        signed = requestOf(signer.signed(request, Method.POST, () -> new ByteArrayInputStream(hiBytes)), hiBytes);
        filter.filter(signed);
        securityContext = (SecurityContext) signed.getAttribute(SecurityContext.ATTRIBUTE_NAME);
        assertEquals("buildService@my-tenant.my-app", securityContext.principal().getName());
        assertEquals(Set.of(Role.buildService(id.tenant(), id.application()),
                            Role.applicationDeveloper(id.tenant(), id.application())),
                     securityContext.roles());

        // Unsigned requests still get no roles.
        filter.filter(unsigned);
        assertNull(unsigned.getAttribute(SecurityContext.ATTRIBUTE_NAME));
    }

    private static DiscFilterRequest requestOf(HttpRequest request, byte[] body) {
        Request converted = new Request(request.uri().toString(), body, Request.Method.valueOf(request.method()));
        converted.getHeaders().addAll(request.headers().map());
        return new ApplicationRequestToDiscFilterRequestWrapper(converted);
    }

}
