// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author bjorncs
 */
@Path("/identity-document")
public interface IdentityDocumentApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    SignedIdentityDocumentEntity getIdentityDocument(@QueryParam("hostname") String hostname);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/node/{host}")
    SignedIdentityDocumentEntity getNodeIdentityDocument(@PathParam("host") String host);


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/tenant/{host}")
    SignedIdentityDocumentEntity getTenantIdentityDocument(@PathParam("host") String host);
}
