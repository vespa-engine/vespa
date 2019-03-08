// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog.Entry;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class AuditLoggerTest {

    private final ControllerTester tester = new ControllerTester();

    @Test
    public void test_logging() {
        Supplier<AuditLog> log = () -> tester.controller().auditLogger().readLog();

        { // GET request is ignored
            HttpRequest request = testRequest(Method.GET, URI.create("http://localhost:8080/os/v1/"), "");
            tester.controller().auditLogger().log(request);
            assertTrue("Not logged", log.get().entries().isEmpty());
        }

        { // PATCH request is logged in audit log
            URI url = URI.create("http://localhost:8080/os/v1/");
            String data = "{\"cloud\":\"cloud9\",\"version\":\"42.0\"}";
            HttpRequest request = testRequest(Method.PATCH, url, data);
            tester.controller().auditLogger().log(request);

            assertEquals(instant(), log.get().entries().get(0).at());
            assertEquals("user", log.get().entries().get(0).principal());
            assertEquals(Entry.Method.PATCH, log.get().entries().get(0).method());
            assertEquals("/os/v1/", log.get().entries().get(0).resource());
            assertEquals(data, log.get().entries().get(0).data().get());
        }

        { // Another PATCH request is logged
            tester.clock().advance(Duration.ofDays(1));
            HttpRequest request = testRequest(Method.PATCH, URI.create("http://localhost:8080/os/v1/"),
                                              "{\"cloud\":\"cloud9\",\"version\":\"43.0\"}");
            tester.controller().auditLogger().log(request);
            assertEquals(2, log.get().entries().size());
            assertEquals(instant(), log.get().entries().get(0).at());
        }

        { // 14 days pass and another PATCH request is logged. Older entries are removed due to expiry
            tester.clock().advance(Duration.ofDays(14));
            HttpRequest request = testRequest(Method.PATCH, URI.create("http://localhost:8080/os/v1/"),
                                              "{\"cloud\":\"cloud9\",\"version\":\"44.0\"}");
            tester.controller().auditLogger().log(request);
            assertEquals(1, log.get().entries().size());
            assertEquals(instant(), log.get().entries().get(0).at());
        }
    }

    private Instant instant() {
        return tester.clock().instant().truncatedTo(MILLIS);
    }

    private static HttpRequest testRequest(Method method, URI url, String data) {
        HttpRequest request = HttpRequest.createTestRequest(
                url.toString(),
                method,
                new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))
        );
        request.getJDiscRequest().setUserPrincipal(() -> "user");
        return request;
    }

}
