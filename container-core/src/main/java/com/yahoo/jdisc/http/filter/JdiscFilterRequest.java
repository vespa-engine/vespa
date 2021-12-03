// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.HttpRequest;

/**
 * JDisc implementation of a filter request.
 *
 */
@Deprecated(forRemoval = true, since = "7.511")
public class JdiscFilterRequest extends DiscFilterRequest {

    private final HttpRequest parent;

    public JdiscFilterRequest(HttpRequest parent) {
        super(parent);
        this.parent = parent;
    }

    @SuppressWarnings("removal")
    @Override
    public HttpRequest getParentRequest() { return parent; }

}
