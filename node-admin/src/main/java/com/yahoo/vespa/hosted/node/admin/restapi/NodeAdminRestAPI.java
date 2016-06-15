// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * @author stiankri
 */
@Path("")
public interface NodeAdminRestAPI {
    @GET
    @Path("/update")
    public String update();
}
