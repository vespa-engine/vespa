// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;

import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 */
@SuppressWarnings("deprecation") // AccessLogEntry.setURI/getURI are deprecated
public class AccessLogRequestLogTest {
    @Test
    public void requireThatQueryWithUnquotedSpecialCharactersIsHandled() {
        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/search/");
        when(httpServletRequest.getQueryString()).thenReturn("query=year:>2010");
        final AccessLogEntry accessLogEntry = new AccessLogEntry();

        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);

        assertThat(accessLogEntry.getURI(), is(not(nullValue())));
    }

    @Test
    public void requireThatDoubleQuotingIsNotPerformed() {
        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        final String path = "/search/";
        when(httpServletRequest.getRequestURI()).thenReturn(path);
        final String query = "query=year%252010+%3B&customParameter=something";
        when(httpServletRequest.getQueryString()).thenReturn(query);
        final AccessLogEntry accessLogEntry = new AccessLogEntry();

        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);

        assertThat(accessLogEntry.getURI().toString(), is(path + '?' + query));

    }

    @Test
    public void requireThatNoQueryPartIsHandledWhenRequestIsMalformed() {
        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        final String path = "/s>earch/";
        when(httpServletRequest.getRequestURI()).thenReturn(path);
        final String query = null;
        when(httpServletRequest.getQueryString()).thenReturn(query);
        final AccessLogEntry accessLogEntry = new AccessLogEntry();

        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);

        assertThat(accessLogEntry.getURI().toString(), is("/s%3Eearch/"));

    }

    @Test
    public void invalid_percent_escape_patterns_in_query_string_are_escaped() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/search/");
        when(httpServletRequest.getQueryString()).thenReturn("q=%%2");

        AccessLogEntry accessLogEntry = new AccessLogEntry();
        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);
        assertThat(accessLogEntry.getURI().toString(), is("/search/?q=%25%252"));
    }

}
