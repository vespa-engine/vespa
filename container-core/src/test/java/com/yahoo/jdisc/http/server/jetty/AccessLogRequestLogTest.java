// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class AccessLogRequestLogTest {

    @Test
    void requireThatQueryWithUnquotedSpecialCharactersIsHandled() {
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, "/search/", "query=year:>2010")
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);

        assertThat(entry.rawPath().get(), is(not(nullValue())));
        assertTrue(entry.rawQuery().isPresent());
    }


    @Test
    void requireThatStatusCodeCanBeOverridden() {
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, "/api/", null)
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        new AccessLogRequestLog(requestLog).log(jettyRequest, JettyMockResponseBuilder.newBuilder().build());
        assertEquals(200, requestLog.entries().remove(0).statusCode().getAsInt());

        jettyRequest.setAttribute(HttpRequestDispatch.ACCESS_LOG_STATUS_CODE_OVERRIDE, 404);
        new AccessLogRequestLog(requestLog).log(jettyRequest, JettyMockResponseBuilder.newBuilder().build());
        assertEquals(404, requestLog.entries().remove(0).statusCode().getAsInt());
    }

    @Test
    void requireThatDoubleQuotingIsNotPerformed() {
        String path = "/search/";
        String query = "query=year%252010+%3B&customParameter=something";
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, path, query)
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);

        assertThat(entry.rawPath().get(), is(path));
        assertThat(entry.rawQuery().get(), is(query));

    }

    @Test
    void raw_path_and_query_are_set_from_request() {
        String rawPath = "//search/";
        String rawQuery = "q=%%2";
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, rawPath, rawQuery)
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.rawPath().get(), is(rawPath));
        Optional<String> actualRawQuery = entry.rawQuery();
        assertThat(actualRawQuery.isPresent(), is(true));
        assertThat(actualRawQuery.get(), is(rawQuery));
    }

    @Test
    void verify_x_forwarded_for_precedence() {
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, "//search/", "q=%%2")
                .header("x-forwarded-for", List.of("1.2.3.4"))
                .header("y-ra", List.of("2.3.4.5"))
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.remoteAddress().get(), is("1.2.3.4"));
    }

    @Test
    void verify_x_forwarded_port_precedence() {
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, "//search/", "q=%%2")
                .header("X-Forwarded-Port", List.of("80"))
                .header("y-rp", List.of("8080"))
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.remotePort().getAsInt(), is(80));
    }

    @Test
    void defaults_to_peer_port_if_remote_port_header_is_invalid() {
        Request jettyRequest = createRequestBuilder()
                .uri("http", "localhost", 12345, "/search/", null)
                .header("X-Forwarded-Port", List.of("8o8o"))
                .header("y-rp", List.of("8o8o"))
                .remote("2.3.4.5", "localhost", 80)
                .build();

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertFalse(entry.remotePort().isPresent());
        assertThat(entry.peerPort().getAsInt(), is(80));
    }

    private void doAccessLoggingOfRequest(RequestLog requestLog, Request jettyRequest) {
        new AccessLogRequestLog(requestLog).log(jettyRequest, createResponseMock());
    }

    private static JettyMockRequestBuilder createRequestBuilder() {
        return JettyMockRequestBuilder.newBuilder()
                .attribute(JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY, new AccessLogEntry())
                .remote("2.3.4.5", "localhost", 12345)
                .localPort(1234);
    }

    private Response createResponseMock() {
        return JettyMockResponseBuilder.newBuilder().build();
    }

}
