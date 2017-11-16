// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.google.inject.Inject;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;

import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author bjorncs
 */
@Path("/instance")
public class InstanceConfirmationResource {

    private static final Logger log = Logger.getLogger(InstanceConfirmationResource.class.getName());

    private final InstanceValidator instanceValidator;

    @Inject
    public InstanceConfirmationResource(@Component AthenzProviderServiceConfig config,
                                        @Component SecretStore secretStore,
                                        @Component SuperModelProvider superModelProvider,
                                        @Component Zone zone) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        SecretStoreKeyProvider keyProvider = new SecretStoreKeyProvider(secretStore, zoneConfig.secretName());
        this.instanceValidator = new InstanceValidator(keyProvider, superModelProvider);
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
