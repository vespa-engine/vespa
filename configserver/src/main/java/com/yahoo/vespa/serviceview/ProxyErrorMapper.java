// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Convert exceptions thrown by the internal REST client into a little more helpful responses.
 *
 * @author Steinar Knutsen
 */
@Provider
public class ProxyErrorMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        StringBuilder msg = new StringBuilder("Invoking (external) web service failed: ");
        msg.append(exception.getMessage());
        return Response.status(500).entity(msg.toString()).type("text/plain").build();
    }

}
