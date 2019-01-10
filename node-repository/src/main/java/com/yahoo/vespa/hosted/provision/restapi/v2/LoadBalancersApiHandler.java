// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.vespa.hosted.provision.NoSuchNodeException;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.yolean.Exceptions;

import javax.inject.Inject;
import java.util.logging.Level;

/**
 * @author mpolden
 */
public class LoadBalancersApiHandler extends LoggingRequestHandler {

    private final NodeRepository nodeRepository;

    @Inject
    public LoadBalancersApiHandler(LoggingRequestHandler.Context parentCtx, NodeRepository nodeRepository) {
        super(parentCtx);
        this.nodeRepository = nodeRepository;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (NotFoundException | NoSuchNodeException e) {
            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
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
        String path = request.getUri().getPath();
        if (path.equals("/loadbalancers/v1/")) return new LoadBalancersResponse(request, nodeRepository);
        throw new NotFoundException("Nothing at path '" + path + "'");
    }
}
