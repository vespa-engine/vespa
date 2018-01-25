// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.status;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;

/**
 * Status handler that outputs config server config and config model versions in use
 *
 * @author hmusum
 */
public class StatusHandler extends HttpHandler {

    private final GlobalComponentRegistry componentRegistry;

    @Inject
    public StatusHandler(Context ctx, GlobalComponentRegistry componentRegistry) {
        super(ctx);
        this.componentRegistry = componentRegistry;
    }

    @Override
    public HttpResponse handleGET(HttpRequest req) {
        return new StatusResponse(OK, componentRegistry);
    }

    private static class StatusResponse extends JSONResponse {

        StatusResponse(int status, GlobalComponentRegistry componentRegistry) {
            super(status);

            Cursor configCursor = object.setObject("configserverConfig");
            SlimeUtils.copyObject(ConfigPayload.fromInstance(componentRegistry.getConfigserverConfig()).getSlime().get(),
                                  configCursor);

            Cursor modelVersionsCursor = object.setArray("modelVersions");
            componentRegistry.getModelFactoryRegistry().getFactories().stream()
                    .map(ModelFactory::getVersion)
                    .map(Version::toString)
                    .forEach(modelVersionsCursor::addString);
        }

    }

}
