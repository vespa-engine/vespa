// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.google.common.collect.Lists;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.http.filter.SecurityResponseFilterChain.RequestViewImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author gjoranv
 */
public class RequestViewImplTest {

    @Test
    void header_from_the_parent_request_is_available() throws Exception {
        final String HEADER = "single-header";

        HeaderFields parentHeaders = new HeaderFields();
        parentHeaders.add(HEADER, "value");

        RequestView requestView = newRequestView(parentHeaders);

        assertEquals(requestView.getFirstHeader(HEADER).get(), "value");
        assertEquals(requestView.getHeaders(HEADER).size(), 1);
        assertEquals(requestView.getHeaders(HEADER).get(0), "value");
    }


    @Test
    void multi_value_header_from_the_parent_request_is_available() throws Exception {
        final String HEADER = "list-header";

        HeaderFields parentHeaders = new HeaderFields();
        parentHeaders.add(HEADER, Lists.newArrayList("one", "two"));

        RequestView requestView = newRequestView(parentHeaders);

        assertEquals(requestView.getHeaders(HEADER).size(), 2);
        assertEquals(requestView.getHeaders(HEADER).get(0), "one");
        assertEquals(requestView.getHeaders(HEADER).get(1), "two");

        assertEquals(requestView.getFirstHeader(HEADER).get(), "one");
    }

    private static RequestView newRequestView(HeaderFields parentHeaders) {
        Request request = mock(Request.class);
        when(request.headers()).thenReturn(parentHeaders);

        return new RequestViewImpl(request);
    }

}
