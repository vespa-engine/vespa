// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;

import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

/**
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class AccessLogRequestLogTest {
    @Test
    public void requireThatQueryWithUnquotedSpecialCharactersIsHandled() {
        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/search/");
        when(httpServletRequest.getQueryString()).thenReturn("query=year:>2010");
        final AccessLogEntry accessLogEntry = new AccessLogEntry();

        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);

        assertThat(accessLogEntry.getRawPath(), is(not(nullValue())));
        assertTrue(accessLogEntry.getRawQuery().isPresent());
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

        assertThat(accessLogEntry.getRawPath(), is(path));
        assertThat(accessLogEntry.getRawQuery().get(), is(query));

    }

    @Test
    public void raw_path_and_query_are_set_from_request() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        String rawPath = "//search/";
        when(httpServletRequest.getRequestURI()).thenReturn(rawPath);
        String rawQuery = "q=%%2";
        when(httpServletRequest.getQueryString()).thenReturn(rawQuery);

        AccessLogEntry accessLogEntry = new AccessLogEntry();
        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);
        assertThat(accessLogEntry.getRawPath(), is(rawPath));
        Optional<String> actualRawQuery = accessLogEntry.getRawQuery();
        assertThat(actualRawQuery.isPresent(), is(true));
        assertThat(actualRawQuery.get(), is(rawQuery));
    }

    @Test
    public void verify_x_forwarded_for_precedence () {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("//search/");
        when(httpServletRequest.getQueryString()).thenReturn("q=%%2");
        when(httpServletRequest.getHeader("x-forwarded-for")).thenReturn("1.2.3.4");
        when(httpServletRequest.getHeader("y-ra")).thenReturn("2.3.4.5");

        AccessLogEntry accessLogEntry = new AccessLogEntry();
        AccessLogRequestLog.populateAccessLogEntryFromHttpServletRequest(httpServletRequest, accessLogEntry);
        assertThat(accessLogEntry.getRemoteAddress(), is("1.2.3.4"));
    }

}
