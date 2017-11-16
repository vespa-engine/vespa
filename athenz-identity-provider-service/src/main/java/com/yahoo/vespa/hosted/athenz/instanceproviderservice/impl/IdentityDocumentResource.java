// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author bjorncs
 */
@Path("/identity-document")
public class IdentityDocumentResource {

    private static final Logger log = Logger.getLogger(IdentityDocumentResource.class.getName());

    private final IdentityDocumentGenerator identityDocumentGenerator;

    @Inject
    public IdentityDocumentResource(@Component AthenzProviderServiceConfig config,
                                    @Component Zone zone,
                                    @Component NodeRepository nodeRepository,
                                    @Component SecretStore secretStore) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        SecretStoreKeyProvider keyProvider = new SecretStoreKeyProvider(secretStore, zoneConfig.secretName());
        this.identityDocumentGenerator =
                new IdentityDocumentGenerator(config, zoneConfig, nodeRepository, zone, keyProvider);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SignedIdentityDocument getIdentityDocument(@QueryParam("hostname") String hostname) {
        // TODO Use TLS client authentication instead of blindly trusting hostname
        if (hostname == null) {
            throw new BadRequestException("The 'hostname' query parameter is missing");
        }
        try {
            log.log(LogLevel.INFO, "Generating identity document for " + hostname);
            return identityDocumentGenerator.generateSignedIdentityDocument(hostname);
        } catch (Exception e) {
            String message = String.format("Unable to generate identity doument [%s]", e.getMessage());
            log.log(LogLevel.ERROR, message, e);
            throw new InternalServerErrorException(message, e);
        }
    }

}
