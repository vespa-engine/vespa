// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog.Entry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class AuditLoggerTest {

    private final ControllerTester tester = new ControllerTester();
    private final Supplier<AuditLog> log = () -> tester.controller().auditLogger().readLog();

    @Test
    void test_logging() {
        { // GET request is ignored
            HttpRequest request = testRequest(Method.GET, URI.create("http://localhost:8080/os/v1/"), "");
            tester.controller().auditLogger().log(request);
            assertTrue(log.get().entries().isEmpty(), "Not logged");
        }

        { // PATCH request is logged in audit log
            URI url = URI.create("http://localhost:8080/os/v1/?foo=bar");
            String data = "{\"cloud\":\"cloud9\",\"version\":\"42.0\"}";
            HttpRequest request = testRequest(Method.PATCH, url, data);
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.PATCH, 1, "/os/v1/?foo=bar");
            assertEquals("user", log.get().entries().get(0).principal());
            assertEquals(data, log.get().entries().get(0).data().get());
        }

        { // Another PATCH request is logged
            tester.clock().advance(Duration.ofDays(1));
            HttpRequest request = testRequest(Method.PATCH, URI.create("http://localhost:8080/os/v1/"),
                    "{\"cloud\":\"cloud9\",\"version\":\"43.0\"}");
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.PATCH, 2, "/os/v1/");
        }

        { // PUT is logged
            tester.clock().advance(Duration.ofDays(1));
            HttpRequest request = testRequest(Method.PUT, URI.create("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/state/dirty/node1/"),
                    "");
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.PUT, 3, "/zone/v2/prod/us-north-1/nodes/v2/state/dirty/node1/");
        }

        { // DELETE is logged
            tester.clock().advance(Duration.ofDays(1));
            HttpRequest request = testRequest(Method.DELETE, URI.create("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/node/node1"),
                    "");
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.DELETE, 4, "/zone/v2/prod/us-north-1/nodes/v2/node/node1");
        }

        { // POST is logged
            tester.clock().advance(Duration.ofDays(1));
            HttpRequest request = testRequest(Method.POST, URI.create("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42"),
                    "6.42");
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.POST, 5, "/controller/v1/jobs/upgrader/confidence/6.42");
        }

        { // 15 days pass and another PATCH request is logged. Older entries are removed due to expiry
            tester.clock().advance(Duration.ofDays(15));
            HttpRequest request = testRequest(Method.PATCH, URI.create("http://localhost:8080/os/v1/"),
                    "{\"cloud\":\"cloud9\",\"version\":\"44.0\"}");
            tester.controller().auditLogger().log(request);
            assertEntry(Entry.Method.PATCH, 1, "/os/v1/");
        }
    }

    private Instant instant() {
        return tester.clock().instant().truncatedTo(MILLIS);
    }

    private void assertEntry(Entry.Method method, int logSize, String resource) {
        assertEquals(logSize, log.get().entries().size());
        assertEquals(instant(), log.get().entries().get(0).at());
        assertEquals(method, log.get().entries().get(0).method());
        assertEquals(resource, log.get().entries().get(0).resource());
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
