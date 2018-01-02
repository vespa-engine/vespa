package com.yahoo.vespa.config.server.http.status;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;


public class StatusHandler extends HttpHandler {

    private final ConfigserverConfig config;
    private final List<String> modelVersions;

    @Inject
    public StatusHandler(Context ctx, GlobalComponentRegistry componentRegistry) {
        super(ctx);
        this.config = componentRegistry.getConfigserverConfig();
        this.modelVersions = componentRegistry.getModelFactoryRegistry().getFactories().stream()
                .map(ModelFactory::getVersion)
                .map(Version::toString)
                .collect(Collectors.toList());
    }

    @Override
    public HttpResponse handleGET(HttpRequest req) {
        return new StatusResponse(OK, config, modelVersions);
    }

    private static class StatusResponse extends JSONResponse {

        StatusResponse(int status, ConfigserverConfig config, List<String> modelVersions) {
            super(status);

            Cursor configCursor = object.setObject("configserverConfig");
            SlimeUtils.copyObject(ConfigPayload.fromInstance(config).getSlime().get(), configCursor);

            Cursor modelVersionsCursor = object.setArray("modelVersions");
            modelVersions.forEach(modelVersionsCursor::addString);
        }

    }

}
