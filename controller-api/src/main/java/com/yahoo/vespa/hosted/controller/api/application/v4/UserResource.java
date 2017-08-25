// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.UserInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author gv
 */
@Path("/v4/user")
@Produces(MediaType.APPLICATION_JSON)
public interface UserResource {
    @GET
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    UserInfo whoAmI(@QueryParam("userOverride") UserId userOverride);

    @PUT
    void createUserTenant();
}
