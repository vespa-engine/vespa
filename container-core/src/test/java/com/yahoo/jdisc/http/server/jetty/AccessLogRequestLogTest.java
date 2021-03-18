// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class AccessLogRequestLogTest {
    @Test
    public void requireThatQueryWithUnquotedSpecialCharactersIsHandled() {
        final Request jettyRequest = createRequestMock();
        when(jettyRequest.getRequestURI()).thenReturn("/search/");
        when(jettyRequest.getQueryString()).thenReturn("query=year:>2010");

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);

        assertThat(entry.rawPath().get(), is(not(nullValue())));
        assertTrue(entry.rawQuery().isPresent());
    }

    @Test
    public void requireThatDoubleQuotingIsNotPerformed() {
        final Request jettyRequest = createRequestMock();
        final String path = "/search/";
        when(jettyRequest.getRequestURI()).thenReturn(path);
        final String query = "query=year%252010+%3B&customParameter=something";
        when(jettyRequest.getQueryString()).thenReturn(query);

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);

        assertThat(entry.rawPath().get(), is(path));
        assertThat(entry.rawQuery().get(), is(query));

    }

    @Test
    public void raw_path_and_query_are_set_from_request() {
        Request jettyRequest = createRequestMock();
        String rawPath = "//search/";
        when(jettyRequest.getRequestURI()).thenReturn(rawPath);
        String rawQuery = "q=%%2";
        when(jettyRequest.getQueryString()).thenReturn(rawQuery);

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.rawPath().get(), is(rawPath));
        Optional<String> actualRawQuery = entry.rawQuery();
        assertThat(actualRawQuery.isPresent(), is(true));
        assertThat(actualRawQuery.get(), is(rawQuery));
    }

    @Test
    public void verify_x_forwarded_for_precedence () {
        Request jettyRequest = createRequestMock();
        when(jettyRequest.getRequestURI()).thenReturn("//search/");
        when(jettyRequest.getQueryString()).thenReturn("q=%%2");
        when(jettyRequest.getHeader("x-forwarded-for")).thenReturn("1.2.3.4");
        when(jettyRequest.getHeader("y-ra")).thenReturn("2.3.4.5");

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.remoteAddress().get(), is("1.2.3.4"));
    }

    @Test
    public void verify_x_forwarded_port_precedence () {
        Request jettyRequest = createRequestMock();
        when(jettyRequest.getRequestURI()).thenReturn("//search/");
        when(jettyRequest.getQueryString()).thenReturn("q=%%2");
        when(jettyRequest.getHeader("X-Forwarded-Port")).thenReturn("80");
        when(jettyRequest.getHeader("y-rp")).thenReturn("8080");

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertThat(entry.remotePort().getAsInt(), is(80));
    }

    @Test
    public void defaults_to_peer_port_if_remote_port_header_is_invalid() {
        final Request jettyRequest = createRequestMock();
        when(jettyRequest.getRequestURI()).thenReturn("/search/");
        when(jettyRequest.getHeader("X-Forwarded-Port")).thenReturn("8o8o");
        when(jettyRequest.getRemotePort()).thenReturn(80);

        InMemoryRequestLog requestLog = new InMemoryRequestLog();
        doAccessLoggingOfRequest(requestLog, jettyRequest);
        RequestLogEntry entry = requestLog.entries().get(0);
        assertFalse(entry.remotePort().isPresent());
        assertThat(entry.peerPort().getAsInt(), is(80));
    }

    private void doAccessLoggingOfRequest(RequestLog requestLog, Request jettyRequest) {
        ServerConfig.AccessLog config = new ServerConfig.AccessLog(
                new ServerConfig.AccessLog.Builder()
                        .remoteAddressHeaders(List.of("x-forwarded-for", "y-ra"))
                        .remotePortHeaders(List.of("X-Forwarded-Port", "y-rp")));
        new AccessLogRequestLog(requestLog, config).log(jettyRequest, createResponseMock());
    }

    private static Request createRequestMock() {
        JDiscServerConnector serverConnector = mock(JDiscServerConnector.class);
        int localPort = 1234;
        when(serverConnector.connectorConfig()).thenReturn(new ConnectorConfig(new ConnectorConfig.Builder().listenPort(localPort)));
        when(serverConnector.getLocalPort()).thenReturn(localPort);
        HttpConnection httpConnection = mock(HttpConnection.class);
        when(httpConnection.getConnector()).thenReturn(serverConnector);
        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("localhost");
        when(request.getRemotePort()).thenReturn(12345);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getScheme()).thenReturn("http");
        when(request.getTimeStamp()).thenReturn(0L);
        when(request.getAttribute(JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY)).thenReturn(new AccessLogEntry());
        when(request.getAttribute("org.eclipse.jetty.server.HttpConnection")).thenReturn(httpConnection);
        return request;
    }

    private Response createResponseMock() {
        Response response = mock(Response.class);
        when(response.getHttpChannel()).thenReturn(mock(HttpChannel.class));
        when(response.getCommittedMetaData()).thenReturn(mock(MetaData.Response.class));
        return response;
    }
}
