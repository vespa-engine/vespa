// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.HttpRequest.Method;

import java.net.URI;
import java.util.Optional;

/**
 * Read-only view of the request for use by SecurityResponseFilters.
 *
 * @author tonytv
 */
public interface RequestView {

    /**
     * Returns a named attribute.
     *
     * @see <a href="http://docs.oracle.com/javaee/7/api/javax/servlet/ServletRequest.html#getAttribute%28java.lang.String%29">javax.servlet.ServletRequest.getAttribute(java.lang.String)</a>
     * @see com.yahoo.jdisc.Request#context()
     * @return the named data associated with the request that are private to this runtime (not exposed to the client)
     */
    public Object getAttribute(String name);

    /**
     * Returns the Http method. Only present if the underlying request has http-like semantics.
     */
    public Optional<Method> getMethod();

    public URI getUri();

}
