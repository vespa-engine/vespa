// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.identitydocument;

import com.google.inject.Inject;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocumentApi;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.logging.Logger;

/**
 * An API that issues signed identity documents for Vespa nodes.
 *
 * @author bjorncs
 */
@Path("/identity-document")
public class IdentityDocumentResource implements IdentityDocumentApi {

    private static final Logger log = Logger.getLogger(IdentityDocumentResource.class.getName());

    private final IdentityDocumentGenerator identityDocumentGenerator;

    @Inject
    public IdentityDocumentResource(@Component IdentityDocumentGenerator identityDocumentGenerator) {
        this.identityDocumentGenerator = identityDocumentGenerator;
    }

    private SignedIdentityDocumentEntity getIdentityDocument(String hostname, IdentityType identityType) {
        if (hostname == null) {
            throw new BadRequestException("The 'hostname' query parameter is missing");
        }
        try {
            return EntityBindingsMapper.toSignedIdentityDocumentEntity(identityDocumentGenerator.generateSignedIdentityDocument(hostname, identityType));
        } catch (Exception e) {
            String message = String.format("Unable to generate identity doument for '%s': %s", hostname, e.getMessage());
            log.log(LogLevel.ERROR, message, e);
            throw new InternalServerErrorException(message, e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/node/{host}")
    @Override
    public SignedIdentityDocumentEntity getNodeIdentityDocument(@PathParam("host") String host) {
        return getIdentityDocument(host, IdentityType.NODE);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/tenant/{host}")
    @Override
    public SignedIdentityDocumentEntity getTenantIdentityDocument(@PathParam("host") String host) {
        return getIdentityDocument(host, IdentityType.TENANT);
    }

}
