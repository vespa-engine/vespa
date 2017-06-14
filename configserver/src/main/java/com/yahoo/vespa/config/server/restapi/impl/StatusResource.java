// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.restapi.impl;

import com.google.common.annotations.Beta;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.http.v2.HttpGetConfigHandler;
import com.yahoo.vespa.config.server.http.v2.HttpListConfigsHandler;
import com.yahoo.vespa.config.server.http.v2.HttpListNamedConfigsHandler;
import com.yahoo.vespa.config.server.http.v2.SessionActiveHandler;
import com.yahoo.vespa.config.server.http.v2.SessionContentHandler;
import com.yahoo.vespa.config.server.http.v2.SessionCreateHandler;
import com.yahoo.vespa.config.server.http.v2.SessionPrepareHandler;
import com.yahoo.vespa.config.server.restapi.resources.StatusInformation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A simple status handler that can provide the status of the config server.
 *
 * @author lulf
 * @since 5.1
 */
@Beta
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {
    private final ConfigserverConfig configserverConfig;
    private final List<String> modelVersions;

    @SuppressWarnings("UnusedParameters")
    public StatusResource(@Component SessionCreateHandler create,
                          @Component SessionContentHandler content,
                          @Component SessionPrepareHandler prepare,
                          @Component SessionActiveHandler active,
                          @Component HttpGetConfigHandler getHandler,
                          @Component HttpListConfigsHandler listHandler,
                          @Component HttpListNamedConfigsHandler listNamedHandler,
                          @Component GlobalComponentRegistry componentRegistry) {
        this.configserverConfig = componentRegistry.getConfigserverConfig();
        this.modelVersions = componentRegistry.getModelFactoryRegistry().getFactories().stream()
                .map(ModelFactory::getVersion).map(Version::toString).collect(Collectors.toList());
    }

    @GET
    public StatusInformation getStatus() {
        return new StatusInformation(configserverConfig, modelVersions);
    }
}
