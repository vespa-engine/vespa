// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.identitydocument;

import com.google.inject.Inject;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocumentApi;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodePrincipal;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
@Path("/identity-document")
public class IdentityDocumentResource implements IdentityDocumentApi {

    private static final Logger log = Logger.getLogger(IdentityDocumentResource.class.getName());

    private final IdentityDocumentGenerator identityDocumentGenerator;
    private final HttpServletRequest request;

    @Inject
    public IdentityDocumentResource(@Component IdentityDocumentGenerator identityDocumentGenerator,
                                    @Context HttpServletRequest request) {
        this.identityDocumentGenerator = identityDocumentGenerator;
        this.request = request;
    }

    private SignedIdentityDocumentEntity getIdentityDocument(String hostname, IdentityType identityType) {
        if (hostname == null) {
            throw new BadRequestException("The 'hostname' query parameter is missing");
        }
        NodePrincipal principal = (NodePrincipal) request.getAttribute(ServletRequest.JDISC_REQUEST_PRINCIPAL);
        String remoteHost;
        if (principal == null) {
            // TODO Remove once self-signed certs are gone
            log.warning("Client is not authenticated - fallback to remote ip");
            remoteHost = request.getRemoteAddr();
        } else {
            remoteHost = principal.getHostIdentityName();
        }
        // TODO Move this check to AuthorizationFilter in node-repository
        if (!identityDocumentGenerator.validateAccess(hostname, remoteHost)) {
            throw new ForbiddenException();
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
