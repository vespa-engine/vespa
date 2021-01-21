// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
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
        final AccessLogEntry accessLogEntry = new AccessLogEntry();
        final Request jettyRequest = createRequestMock(accessLogEntry);
        when(jettyRequest.getRequestURI()).thenReturn("/search/");
        when(jettyRequest.getQueryString()).thenReturn("query=year:>2010");

        doAccessLoggingOfRequest(jettyRequest);

        assertThat(accessLogEntry.getRawPath(), is(not(nullValue())));
        assertTrue(accessLogEntry.getRawQuery().isPresent());
    }

    @Test
    public void requireThatDoubleQuotingIsNotPerformed() {
        final AccessLogEntry accessLogEntry = new AccessLogEntry();
        final Request jettyRequest = createRequestMock(accessLogEntry);
        final String path = "/search/";
        when(jettyRequest.getRequestURI()).thenReturn(path);
        final String query = "query=year%252010+%3B&customParameter=something";
        when(jettyRequest.getQueryString()).thenReturn(query);

        doAccessLoggingOfRequest(jettyRequest);

        assertThat(accessLogEntry.getRawPath(), is(path));
        assertThat(accessLogEntry.getRawQuery().get(), is(query));

    }

    @Test
    public void raw_path_and_query_are_set_from_request() {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        Request jettyRequest = createRequestMock(accessLogEntry);
        String rawPath = "//search/";
        when(jettyRequest.getRequestURI()).thenReturn(rawPath);
        String rawQuery = "q=%%2";
        when(jettyRequest.getQueryString()).thenReturn(rawQuery);

        doAccessLoggingOfRequest(jettyRequest);
        assertThat(accessLogEntry.getRawPath(), is(rawPath));
        Optional<String> actualRawQuery = accessLogEntry.getRawQuery();
        assertThat(actualRawQuery.isPresent(), is(true));
        assertThat(actualRawQuery.get(), is(rawQuery));
    }

    @Test
    public void verify_x_forwarded_for_precedence () {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        Request jettyRequest = createRequestMock(accessLogEntry);
        when(jettyRequest.getRequestURI()).thenReturn("//search/");
        when(jettyRequest.getQueryString()).thenReturn("q=%%2");
        when(jettyRequest.getHeader("x-forwarded-for")).thenReturn("1.2.3.4");
        when(jettyRequest.getHeader("y-ra")).thenReturn("2.3.4.5");

        doAccessLoggingOfRequest(jettyRequest);
        assertThat(accessLogEntry.getRemoteAddress(), is("1.2.3.4"));
    }

    @Test
    public void verify_x_forwarded_port_precedence () {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        Request jettyRequest = createRequestMock(accessLogEntry);
        when(jettyRequest.getRequestURI()).thenReturn("//search/");
        when(jettyRequest.getQueryString()).thenReturn("q=%%2");
        when(jettyRequest.getHeader("X-Forwarded-Port")).thenReturn("80");
        when(jettyRequest.getHeader("y-rp")).thenReturn("8080");

        doAccessLoggingOfRequest(jettyRequest);
        assertThat(accessLogEntry.getRemotePort(), is(80));
    }

    @Test
    public void defaults_to_peer_port_if_remote_port_header_is_invalid() {
        final AccessLogEntry accessLogEntry = new AccessLogEntry();
        final Request jettyRequest = createRequestMock(accessLogEntry);
        when(jettyRequest.getRequestURI()).thenReturn("/search/");
        when(jettyRequest.getHeader("X-Forwarded-Port")).thenReturn("8o8o");
        when(jettyRequest.getRemotePort()).thenReturn(80);

        doAccessLoggingOfRequest(jettyRequest);
        assertThat(accessLogEntry.getRemotePort(), is(0));
        assertThat(accessLogEntry.getPeerPort(), is(80));
    }

    private void doAccessLoggingOfRequest(Request jettyRequest) {
        ServerConfig.AccessLog config = new ServerConfig.AccessLog(
                new ServerConfig.AccessLog.Builder()
                        .remoteAddressHeaders(List.of("x-forwarded-for", "y-ra"))
                        .remotePortHeaders(List.of("X-Forwarded-Port", "y-rp")));
        new AccessLogRequestLog(mock(RequestLog.class), config).log(jettyRequest, createResponseMock());
    }

    private static Request createRequestMock(AccessLogEntry entry) {
        ServerConnector serverConnector = mock(ServerConnector.class);
        when(serverConnector.getLocalPort()).thenReturn(1234);
        HttpConnection httpConnection = mock(HttpConnection.class);
        when(httpConnection.getConnector()).thenReturn(serverConnector);
        Request request = mock(Request.class);
        when(request.getAttribute(JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY)).thenReturn(entry);
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
