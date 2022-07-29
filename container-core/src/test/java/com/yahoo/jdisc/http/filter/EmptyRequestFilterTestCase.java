// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.chain.EmptyRequestFilter;
import com.yahoo.jdisc.service.CurrentContainer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static com.yahoo.jdisc.http.HttpRequest.Version;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Simon Thoresen Hult
 */
public class EmptyRequestFilterTestCase {

    @Test
    void requireThatEmptyFilterDoesNothing() throws Exception {
        final HttpRequest lhs = newRequest(Method.GET, "/status.html", Version.HTTP_1_1);
        final HttpRequest rhs = newRequest(Method.GET, "/status.html", Version.HTTP_1_1);

        EmptyRequestFilter.INSTANCE.filter(rhs, mock(ResponseHandler.class));

        assertEquals(lhs.headers(), rhs.headers());
        assertEquals(lhs.context(), rhs.context());
        assertEquals(lhs.getTimeout(TimeUnit.MILLISECONDS), rhs.getTimeout(TimeUnit.MILLISECONDS));
        assertEquals(lhs.parameters(), rhs.parameters());
        assertEquals(lhs.getMethod(), rhs.getMethod());
        assertEquals(lhs.getVersion(), rhs.getVersion());
        assertEquals(lhs.getRemoteAddress(), rhs.getRemoteAddress());
    }

    private static HttpRequest newRequest(
            final Method method, final String uri, final Version version) {
        final CurrentContainer currentContainer = mock(CurrentContainer.class);
        when(currentContainer.newReference(any(URI.class))).thenReturn(mock(Container.class));
        when(currentContainer.newReference(any(URI.class), any(Object.class))).thenReturn(mock(Container.class));
        return HttpRequest.newServerRequest(currentContainer, URI.create(uri), method, version);
    }
}
