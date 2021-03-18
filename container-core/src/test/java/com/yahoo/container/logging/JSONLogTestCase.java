// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.yolean.trace.TraceNode;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;


/**
 * @author frodelu
 */
public class JSONLogTestCase {

    private static String ipAddress = "152.200.54.243";

    private RequestLogEntry.Builder newRequestLogEntry(final String query) {
        return newRequestLogEntry(query, new Coverage(100,100,100,0));
    }
    private RequestLogEntry.Builder newRequestLogEntry(final String query, Coverage coverage) {
        return new RequestLogEntry.Builder()
                .rawQuery("query=" + query)
                .rawPath("")
                .peerAddress(ipAddress)
                .httpMethod("GET")
                .httpVersion("HTTP/1.1")
                .userAgent("Mozilla/4.05 [en] (Win95; I)")
                .hitCounts(new HitCounts(0, 10, 1234, 0, 10, coverage))
                .hostString("localhost")
                .statusCode(200)
                .timestamp(Instant.ofEpochMilli(920880005023L))
                .duration(Duration.ofMillis(122))
                .contentSize(9875)
                .localPort(0)
                .peerPort(0);
    }

    @Test
    public void test_json_log_entry() {
        RequestLogEntry entry = newRequestLogEntry("test").build();

         String expectedOutput =
            "{\"ip\":\"152.200.54.243\"," +
            "\"peeraddr\":\"152.200.54.243\"," +
            "\"time\":920880005.023," +
            "\"duration\":0.122," +
            "\"responsesize\":9875," +
            "\"code\":200," +
            "\"method\":\"GET\"," +
            "\"uri\":\"?query=test\"," +
            "\"version\":\"HTTP/1.1\"," +
            "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
            "\"host\":\"localhost\"," +
            "\"scheme\":null," +
            "\"localport\":0," +
            "\"search\":{" +
            "\"totalhits\":1234," +
            "\"hits\":0," +
            "\"coverage\":{\"coverage\":100,\"documents\":100}" +
            "}" +
            "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);
    }
    @Test
    public void test_json_of_trace() {
        TraceNode root = new TraceNode("root", 7);
        RequestLogEntry entry = newRequestLogEntry("test")
                .traceNode(root)
                .build();

        String expectedOutput =
                "{\"ip\":\"152.200.54.243\"," +
                "\"peeraddr\":\"152.200.54.243\"," +
                "\"time\":920880005.023," +
                "\"duration\":0.122," +
                "\"responsesize\":9875," +
                "\"code\":200," +
                "\"method\":\"GET\"," +
                "\"uri\":\"?query=test\"," +
                "\"version\":\"HTTP/1.1\"," +
                "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
                "\"host\":\"localhost\"," +
                "\"scheme\":null," +
                "\"localport\":0," +
                "\"trace\":{\"timestamp\":0,\"message\":\"root\"}," +
                "\"search\":{" +
                "\"totalhits\":1234," +
                "\"hits\":0," +
                "\"coverage\":{\"coverage\":100,\"documents\":100}" +
                "}" +
                "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);
    }
    @Test
    public void test_with_keyvalues() {
        RequestLogEntry entry = newRequestLogEntry("test")
                .addExtraAttribute("singlevalue", "value1")
                .addExtraAttribute("multivalue", "value2")
                .addExtraAttribute("multivalue", "value3")
                .build();

        String expectedOutput =
            "{\"ip\":\"152.200.54.243\"," +
            "\"peeraddr\":\"152.200.54.243\"," +
            "\"time\":920880005.023," +
            "\"duration\":0.122," +
            "\"responsesize\":9875," +
            "\"code\":200," +
            "\"method\":\"GET\"," +
            "\"uri\":\"?query=test\"," +
            "\"version\":\"HTTP/1.1\"," +
            "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
            "\"host\":\"localhost\"," +
            "\"scheme\":null," +
            "\"localport\":0," +
            "\"search\":{" +
            "\"totalhits\":1234," +
            "\"hits\":0," +
            "\"coverage\":{\"coverage\":100,\"documents\":100}" +
            "}," +
            "\"attributes\":{" +
            "\"singlevalue\":\"value1\"," +
            "\"multivalue\":[\"value2\",\"value3\"]}" +
            "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);

    }

    @Test
    public void test_with_remoteaddrport() throws Exception {
        RequestLogEntry entry = newRequestLogEntry("test")
                .remoteAddress("FE80:0000:0000:0000:0202:B3FF:FE1E:8329")
                .build();

        String expectedOutput =
            "{\"ip\":\"152.200.54.243\"," +
            "\"peeraddr\":\"152.200.54.243\"," +
            "\"time\":920880005.023," +
            "\"duration\":0.122," +
            "\"responsesize\":9875," +
            "\"code\":200," +
            "\"method\":\"GET\"," +
            "\"uri\":\"?query=test\"," +
            "\"version\":\"HTTP/1.1\"," +
            "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
            "\"host\":\"localhost\"," +
            "\"scheme\":null," +
            "\"localport\":0," +
            "\"remoteaddr\":\"FE80:0000:0000:0000:0202:B3FF:FE1E:8329\"," +
            "\"search\":{" +
            "\"totalhits\":1234," +
            "\"hits\":0," +
            "\"coverage\":{\"coverage\":100,\"documents\":100}" +
            "}" +
            "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);

        // Add remote port and verify
        entry = newRequestLogEntry("test")
                .remoteAddress("FE80:0000:0000:0000:0202:B3FF:FE1E:8329")
                .remotePort(1234)
                .build();

        expectedOutput =
            "{\"ip\":\"152.200.54.243\"," +
            "\"peeraddr\":\"152.200.54.243\"," +
            "\"time\":920880005.023," +
            "\"duration\":0.122," +
            "\"responsesize\":9875," +
            "\"code\":200," +
            "\"method\":\"GET\"," +
            "\"uri\":\"?query=test\"," +
            "\"version\":\"HTTP/1.1\"," +
            "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
            "\"host\":\"localhost\"," +
            "\"scheme\":null," +
            "\"localport\":0," +
            "\"remoteaddr\":\"FE80:0000:0000:0000:0202:B3FF:FE1E:8329\"," +
            "\"remoteport\":1234," +
            "\"search\":{" +
            "\"totalhits\":1234," +
            "\"hits\":0," +
            "\"coverage\":{\"coverage\":100,\"documents\":100}" +
            "}" +
            "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);
    }

    @Test
    public void test_remote_address_same_as_ip_address() throws Exception {
        RequestLogEntry entry = newRequestLogEntry("test").build();
        RequestLogEntry entrywithremote = newRequestLogEntry("test")
                .remoteAddress(entry.peerAddress().get())
                .build();
        JSONFormatter formatter = new JSONFormatter();
        assertJsonEquals(formatEntry(entry), formatEntry(entrywithremote));
    }

    @Test
    public void test_useragent_with_quotes() {
        RequestLogEntry entry = new RequestLogEntry.Builder()
                .rawQuery("query=test")
                .rawPath("")
                .peerAddress(ipAddress)
                .httpMethod("GET")
                .httpVersion("HTTP/1.1")
                .userAgent("Mozilla/4.05 [en] (Win95; I; \"Best Browser Ever\")")
                .hitCounts(new HitCounts(0, 10, 1234, 0, 10, new Coverage(100, 200, 200, 0)))
                .hostString("localhost")
                .statusCode(200)
                .timestamp(Instant.ofEpochMilli(920880005023L))
                .duration(Duration.ofMillis(122))
                .contentSize(9875)
                .localPort(0)
                .peerPort(0)
                .build();

        String expectedOutput =
            "{\"ip\":\"152.200.54.243\"," +
            "\"peeraddr\":\"152.200.54.243\"," +
            "\"time\":920880005.023," +
            "\"duration\":0.122," +
            "\"responsesize\":9875," +
            "\"code\":200," +
            "\"method\":\"GET\"," +
            "\"uri\":\"?query=test\"," +
            "\"version\":\"HTTP/1.1\"," +
            "\"agent\":\"Mozilla/4.05 [en] (Win95; I; \\\"Best Browser Ever\\\")\"," +
            "\"host\":\"localhost\"," +
            "\"scheme\":null," +
            "\"localport\":0," +
            "\"search\":{" +
            "\"totalhits\":1234," +
            "\"hits\":0," +
            "\"coverage\":{\"coverage\":50,\"documents\":100,\"degraded\":{\"non-ideal-state\":true}}" +
            "}" +
            "}";

        assertJsonEquals(formatEntry(entry), expectedOutput);
    }

    private void verifyCoverage(String coverage, RequestLogEntry entry) {
        assertJsonEquals(formatEntry(entry),
                "{\"ip\":\"152.200.54.243\"," +
                "\"peeraddr\":\"152.200.54.243\"," +
                "\"time\":920880005.023," +
                "\"duration\":0.122," +
                "\"responsesize\":9875," +
                "\"code\":200," +
                "\"method\":\"GET\"," +
                "\"uri\":\"?query=test\"," +
                "\"version\":\"HTTP/1.1\"," +
                "\"agent\":\"Mozilla/4.05 [en] (Win95; I)\"," +
                "\"host\":\"localhost\"," +
                "\"scheme\":null," +
                "\"localport\":0," +
                "\"search\":{" +
                "\"totalhits\":1234," +
                "\"hits\":0," +
                coverage +
                "}" +
                "}");
    }

    @Test
    public void test_with_coverage_degradation() {
        verifyCoverage("\"coverage\":{\"coverage\":50,\"documents\":100,\"degraded\":{\"non-ideal-state\":true}}",
                       newRequestLogEntry("test",  new Coverage(100,200,200,0)).build());
        verifyCoverage("\"coverage\":{\"coverage\":50,\"documents\":100,\"degraded\":{\"match-phase\":true}}",
                       newRequestLogEntry("test",  new Coverage(100,200,200,1)).build());
        verifyCoverage("\"coverage\":{\"coverage\":50,\"documents\":100,\"degraded\":{\"timeout\":true}}",
                       newRequestLogEntry("test",  new Coverage(100,200,200,2)).build());
        verifyCoverage("\"coverage\":{\"coverage\":50,\"documents\":100,\"degraded\":{\"adaptive-timeout\":true}}",
                       newRequestLogEntry("test",  new Coverage(100,200,200,4)).build());
    }

    private String formatEntry(RequestLogEntry entry) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            new JSONFormatter().write(entry, outputStream);
            return outputStream.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
