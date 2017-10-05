// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.ResourceResponse;
import com.yahoo.yolean.Exceptions;

import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * This implements the controller/v1 API which provides operators with information about,
 * and control over the Controller.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // Created by injection
public class ControllerApiHandler extends LoggingRequestHandler {

    private final ControllerMaintenance maintenance;

    public ControllerApiHandler(Executor executor, AccessLog accessLog, ControllerMaintenance maintenance) {
        super(executor, accessLog);
        this.maintenance = maintenance;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case POST: return handlePOST(request);
                case DELETE: return handleDELETE(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }
    
    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/controller/v1/")) return root(request);
        if (path.matches("/controller/v1/maintenance/")) return new JobsResponse(maintenance.jobControl());
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/controller/v1/maintenance/inactive/{jobName}")) 
            return setActive(path.get("jobName"), false);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/controller/v1/maintenance/inactive/{jobName}"))
            return setActive(path.get("jobName"), true);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse root(HttpRequest request) {
        return new ResourceResponse(request, "maintenance");
    }

    private HttpResponse setActive(String jobName, boolean active) {
        if ( ! maintenance.jobControl().jobs().contains(jobName))
            return ErrorResponse.notFoundError("No job named '" + jobName + "'");
        maintenance.jobControl().setActive(jobName, active);
        return new MessageResponse((active ? "Re-activated" : "Deactivated" ) + " job '" + jobName + "'");
    }

}
