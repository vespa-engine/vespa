// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.yolean.Exceptions;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API for user management related to access control.
 *
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class UserApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(UserApiHandler.class.getName());

    public UserApiHandler(Context parentCtx) {
        super(parentCtx);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case PUT: return handlePUT(request);
                case POST: return handlePOST(request);
                case DELETE: return handleDELETE(request);
                case OPTIONS: return handleOPTIONS();
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
        Path path = new Path(request.getUri());


        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handlePUT(HttpRequest request) {
        Path path = new Path(request.getUri());


        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());


        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());


        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleOPTIONS() {
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,PUT,POST,PATCH,DELETE,OPTIONS");
        return response;
    }


    private List<TenantRole> tenantRoles(TenantName tenant) { // TODO jvenstad: Move these two to CloudRoles utility class.
        return List.of(roles.tenantOperator(tenant),
                       roles.tenantAdmin(tenant),
                       roles.tenantOwner(tenant));
    }

    private List<ApplicationRole> applicationRoles(TenantName tenant, ApplicationName application) {
        return List.of(roles.applicationReader(tenant, application),
                       roles.applicationDeveloper(tenant, application),
                       roles.applicationOperator(tenant, application),
                       roles.applicationAdmin(tenant, application));
    }

}
