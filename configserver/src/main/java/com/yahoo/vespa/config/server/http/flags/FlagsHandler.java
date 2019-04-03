// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.flags;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.configserver.flags.FlagsDb;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.yolean.Exceptions;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;

/**
 * Handles /flags/v1 requests
 *
 * @author hakonhall
 */
public class FlagsHandler extends HttpHandler {
    private final FlagsDb flagsDb;

    @Inject
    public FlagsHandler(LoggingRequestHandler.Context context, FlagsDb flagsDb) {
        super(context);
        this.flagsDb = flagsDb;
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/flags/v1")) return new V1Response(flagsV1Uri(request), "data", "defined");
        if (path.matches("/flags/v1/data")) return getFlagDataList(request);
        if (path.matches("/flags/v1/data/{flagId}")) return getFlagData(findFlagId(request, path));
        if (path.matches("/flags/v1/defined")) return new DefinedFlags(Flags.getAllFlags());
        if (path.matches("/flags/v1/defined/{flagId}")) return getDefinedFlag(findFlagId(request, path));
        throw new NotFoundException("Nothing at path '" + path + "'");
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/flags/v1/data/{flagId}")) return putFlagData(request, findFlagId(request, path));
        throw new NotFoundException("Nothing at path '" + path + "'");
    }

    @Override
    protected HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/flags/v1/data/{flagId}")) return deleteFlagData(findFlagId(request, path));
        throw new NotFoundException("Nothing at path '" + path + "'");
    }

    private String flagsV1Uri(HttpRequest request) {
        URI uri = request.getUri();
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port + "/flags/v1";
    }

    private HttpResponse getDefinedFlag(FlagId flagId) {
        FlagDefinition definition = Flags.getFlag(flagId)
                .orElseThrow(() -> new NotFoundException("Flag " + flagId + " not defined"));
        return new DefinedFlag(definition);
    }

    private HttpResponse getFlagDataList(HttpRequest request) {
        return new FlagDataListResponse(flagsV1Uri(request), flagsDb.getAllFlags(),
                Objects.equals(request.getProperty("recursive"), "true"));
    }

    private HttpResponse getFlagData(FlagId flagId) {
        FlagData data = flagsDb.getValue(flagId).orElseThrow(() -> new NotFoundException("Flag " + flagId + " not set"));
        return new FlagDataResponse(data);
    }

    private HttpResponse putFlagData(HttpRequest request, FlagId flagId) {
        FlagData data;
        try {
            data = FlagData.deserialize(request.getData());
        } catch (UncheckedIOException e) {
            return HttpErrorResponse.badRequest("Failed to deserialize request data: " + Exceptions.toMessageString(e));
        }

        if (!isForce(request)) {
            FlagDefinition definition = Flags.getFlag(flagId).get(); // FlagId has been validated in findFlagId()
            data.validate(definition.getUnboundFlag().serializer());
        }

        flagsDb.setValue(flagId, data);
        return new OKResponse();
    }

    private HttpResponse deleteFlagData(FlagId flagId) {
        flagsDb.removeValue(flagId);
        return new OKResponse();
    }

    private FlagId findFlagId(HttpRequest request, Path path) {
        FlagId flagId = new FlagId(path.get("flagId"));

        if (!isForce(request)) {
            Flags.getFlag(flagId).orElseThrow(() ->
                    new NotFoundException("There is no flag '" + flagId + "' (use ?force=true to override)"));
        }

        return flagId;
    }

    private boolean isForce(HttpRequest request) {
        return Objects.equals(request.getProperty("force"), "true");
    }
}
