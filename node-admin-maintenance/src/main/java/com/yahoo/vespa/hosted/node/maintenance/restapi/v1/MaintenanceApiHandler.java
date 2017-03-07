// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance.restapi.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;


/**
 * @author freva
 */
class MaintenanceApiHandler extends LoggingRequestHandler {
    private final static ObjectMapper objectMapper = new ObjectMapper();

    public MaintenanceApiHandler(Executor executor, AccessLog accessLog) {
        super(executor, accessLog);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case POST: return handlePOST(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
//        catch (NotFoundException e) {
//            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
//        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handlePOST(HttpRequest request) {
        switch (request.getUri().getPath()) {
            case "/maintenance/v1":
                try {
                    List<Maintainer.MaintenanceJob> maintenanceJobs = objectMapper.readValue(
                            request.getData(), new TypeReference<List<Maintainer.MaintenanceJob>>(){});
                    Maintainer.executeJobs(maintenanceJobs);
                } catch (IOException e) {
                    throw new RuntimeException("Failed parsing JSON request", e);
                }
                return new MessageResponse("Successfully executed command");
            default:
                return ErrorResponse.notFoundError("Fail");
//                throw new NotFoundException("Nothing at path '" + request.getUri().getPath() + "'");
        }
    }
}
