// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.status;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;

/**
 * Status handler that outputs config server config and config model versions in use
 *
 * @author hmusum
 */
public class StatusHandler extends HttpHandler {

    private final ModelFactoryRegistry modelFactoryRegistry;
    private final ConfigserverConfig configserverConfig;

    @Inject
    public StatusHandler(Context ctx, ModelFactoryRegistry modelFactoryRegistry, ConfigserverConfig configserverConfig) {
        super(ctx);
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.configserverConfig = configserverConfig;
    }

    @Override
    public HttpResponse handleGET(HttpRequest req) {
        return new StatusResponse(OK, modelFactoryRegistry, configserverConfig);
    }

    private static class StatusResponse extends JSONResponse {

        StatusResponse(int status, ModelFactoryRegistry modelFactoryRegistry, ConfigserverConfig configserverConfig) {
            super(status);

            Cursor configCursor = object.setObject("configserverConfig");
            SlimeUtils.copyObject(ConfigPayload.fromInstance(configserverConfig).getSlime().get(),
                                  configCursor);

            Cursor modelVersionsCursor = object.setArray("modelVersions");
            modelFactoryRegistry.getFactories().stream()
                                .map(ModelFactory::version)
                                .map(Version::toFullString)
                                .forEach(modelVersionsCursor::addString);
        }

    }

}
