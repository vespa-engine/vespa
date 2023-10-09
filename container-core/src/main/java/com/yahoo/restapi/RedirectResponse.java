// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.jdisc.Response;

import java.net.URI;

/**
 * A HTTP redirect response
 *
 * @author bjorncs
 */
public class RedirectResponse extends MessageResponse {

    private RedirectResponse(int statusCode, URI location) {
        super(statusCode, "Moved to " + location.toString());
        headers().add("Location", location.toString());
    }

    public static RedirectResponse found(URI location) {
        return new RedirectResponse(Response.Status.FOUND, location);
    }

    public static RedirectResponse movedPermanently(URI location) {
        return new RedirectResponse(Response.Status.MOVED_PERMANENTLY, location);
    }
}
