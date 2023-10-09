// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig.Builder;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig.Clients;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig.Clients.Tokens;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class CloudTokenDataPlaneHandlerTest {

    @Test
    void testFingerprints() throws IOException {
        CloudTokenDataPlaneHandler handler = new CloudTokenDataPlaneHandler(
                new Builder().tokenContext("context")
                             .clients(new Clients.Builder().id("client1")
                                                           .permissions("read")
                                                           .tokens(new Tokens.Builder().id("id1")
                                                                                       .fingerprints(List.of("pinky", "ring", "middle", "index", "thumb"))
                                                                                       .checkAccessHashes(List.of("a", "b", "c", "d", "e"))
                                                                                       .expirations(List.of("<none>", "<none>", "<none>", "<none>", "<none>")))
                                                           .tokens(new Tokens.Builder().id("id2")
                                                                                       .fingerprints("toasty")
                                                                                       .checkAccessHashes("hash")
                                                                                       .expirations("<none>")))
                             .clients(new Clients.Builder().id("client2")
                                                           .permissions("write")
                                                           .tokens(new Tokens.Builder().id("id2")
                                                                                       .fingerprints("toasty")
                                                                                       .checkAccessHashes("hash")
                                                                                       .expirations("<none>")))
                             .build(),
                Runnable::run
        );

        HttpResponse response = handler.handle(createTestRequest("", GET));
        assertEquals(200,
                     response.getStatus());
        assertEquals("""
                     {"tokens":[{"id":"id1","fingerprints":["index","middle","pinky","ring","thumb"]},{"id":"id2","fingerprints":["toasty"]}]}""",
                     new ByteArrayOutputStream() {{ response.render(this); }}.toString(UTF_8));
    }

}
