// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * @author bjorncs
 */
@Path("/{path: instance|refresh}")
public class InstanceConfirmationResource {

    private static final Logger log = Logger.getLogger(InstanceConfirmationResource.class.getName());

    private final InstanceValidator instanceValidator;

    @Inject
    public InstanceConfirmationResource(@Component InstanceValidator instanceValidator) {
        this.instanceValidator = instanceValidator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InstanceConfirmation confirmInstance(InstanceConfirmation instanceConfirmation) {
        if (!instanceValidator.isValidInstance(instanceConfirmation)) {
            log.log(LogLevel.ERROR, "Invalid instance: " + instanceConfirmation);
            throw new ForbiddenException("Instance is invalid");
        }
        return instanceConfirmation;
    }
}
