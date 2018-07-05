// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.chain.EmptyResponseFilter;
import com.yahoo.jdisc.service.CurrentContainer;
import org.testng.annotations.Test;

import java.net.URI;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static com.yahoo.jdisc.http.HttpRequest.Version;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class EmptyResponseFilterTestCase {

    @Test
    public void requireThatEmptyFilterDoesNothing() throws Exception {
        final HttpRequest request = newRequest(Method.GET, "/status.html", Version.HTTP_1_1);
        final HttpResponse lhs = HttpResponse.newInstance(Response.Status.OK);
        final HttpResponse rhs = HttpResponse.newInstance(Response.Status.OK);

        EmptyResponseFilter.INSTANCE.filter(lhs, null);

        assertEquals(lhs.headers(), rhs.headers());
        assertEquals(lhs.context(), rhs.context());
        assertEquals(lhs.getError(), rhs.getError());
        assertEquals(lhs.getMessage(), rhs.getMessage());
    }

    private static HttpRequest newRequest(final Method method, final String uri, final Version version) {
        final CurrentContainer currentContainer = mock(CurrentContainer.class);
        when(currentContainer.newReference(any(URI.class))).thenReturn(mock(Container.class));
        return HttpRequest.newServerRequest(currentContainer, URI.create(uri), method, version);
    }
}
