// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.HttpRequest.Method;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the request for use by SecurityResponseFilters.
 *
 * @author Tony Vaagenes
 */
public interface RequestView {

    /**
     * Returns a named attribute.
     *
     * @see <a href="http://docs.oracle.com/javaee/7/api/javax/servlet/ServletRequest.html#getAttribute%28java.lang.String%29">javax.servlet.ServletRequest.getAttribute(java.lang.String)</a>
     * @see com.yahoo.jdisc.Request#context()
     * @return the named data associated with the request that are private to this runtime (not exposed to the client)
     */
    Object getAttribute(String name);

    /**
     * Returns an immutable view of all values of a named header field.
     * Returns an empty list if no such header is present.
     */
    @Nonnull
    List<String> getHeaders(String name);

    /**
     * Convenience method for retrieving the first value of a named header field.
     * Returns empty if the header is not set, or if the value list is empty.
     */
    Optional<String> getFirstHeader(String name);

    /**
     * Returns the Http method. Only present if the underlying request has http-like semantics.
     */
    Optional<Method> getMethod();

    URI getUri();

}
