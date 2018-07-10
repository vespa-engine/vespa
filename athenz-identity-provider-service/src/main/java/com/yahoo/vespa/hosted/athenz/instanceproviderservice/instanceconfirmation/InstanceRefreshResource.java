// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation;

import com.google.inject.Inject;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;

import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.logging.Logger;

/**
 * ZTS calls this resource when it's requested to refresh an instance certificate
 *
 * @author bjorncs
 */
@Path("/refresh")
public class InstanceRefreshResource {

    private static final Logger log = Logger.getLogger(InstanceRefreshResource.class.getName());

    private final InstanceValidator instanceValidator;

    @Inject
    public InstanceRefreshResource(@Component InstanceValidator instanceValidator) {
        this.instanceValidator = instanceValidator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InstanceConfirmation confirmInstanceRefresh(InstanceConfirmation instanceConfirmation) {
        log.log(LogLevel.DEBUG, instanceConfirmation.toString());
        if (!instanceValidator.isValidRefresh(instanceConfirmation)) {
            log.log(LogLevel.ERROR, "Invalid instance refresh: " + instanceConfirmation);
            throw new ForbiddenException("Instance is invalid");
        }
        return instanceConfirmation;
    }
}
