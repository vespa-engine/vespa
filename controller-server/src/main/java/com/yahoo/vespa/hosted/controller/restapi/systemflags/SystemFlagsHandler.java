// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.JacksonJsonResponse;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.SystemFlagsDataArchive;

import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Handler implementation for '/system-flags/v1', an API for controlling system-wide feature flags
 *
 * @author bjorncs
 */
@SuppressWarnings("unused") // Request handler listed in controller's services.xml
public class SystemFlagsHandler extends LoggingRequestHandler {

    private static final String API_PREFIX = "/system-flags/v1";

    private final SystemFlagsDeployer deployer;

    @Inject
    public SystemFlagsHandler(ZoneRegistry zoneRegistry,
                              ServiceIdentityProvider identityProvider,
                              Executor executor) {
        super(executor);
        this.deployer = new SystemFlagsDeployer(identityProvider, zoneRegistry.system(), FlagsTarget.getAllTargetsInSystem(zoneRegistry, true));
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        switch (request.getMethod()) {
            case PUT:
                return put(request);
            default:
                return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
        }
    }

    private HttpResponse put(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches(API_PREFIX + "/deploy")) return deploy(request, /*dryRun*/false);
        if (path.matches(API_PREFIX + "/dryrun")) return deploy(request, /*dryRun*/true);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse deploy(HttpRequest request, boolean dryRun) {
        try {
            String contentType = request.getHeader("Content-Type");
            if (!contentType.equalsIgnoreCase("application/zip")) {
                return ErrorResponse.badRequest("Invalid content type: " + contentType);
            }
            SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromZip(request.getData());
            SystemFlagsDeployResult result = deployer.deployFlags(archive, dryRun);
            return new JacksonJsonResponse<>(200, result.toWire());
        } catch (Exception e) {
            String errorMessage = "System flags deploy failed: " + e.getMessage();
            log.log(Level.SEVERE, errorMessage, e);
            return ErrorResponse.internalServerError(errorMessage);
        }
    }

}
