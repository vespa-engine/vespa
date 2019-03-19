// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.yolean.Exceptions;

import java.io.OutputStream;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This API proxies requests to an Athenz server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class AthenzProxyHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(AthenzProxyHandler.class.getName());

    private final AthenzFacade athenz;

    public AthenzProxyHandler(Context parentCtx, AthenzFacade athenz) {
        super(parentCtx);
        this.athenz = athenz;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Method method = request.getMethod();
        try {
            switch (method) {
                case GET: return get(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + method + "' is unsupported");
            }
        } catch (IllegalArgumentException|IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}")) return badge(path.get("tenant"), path.get("application"), path.get("instance"));
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}/{jobName}")) return badge(path.get("tenant"), path.get("application"), path.get("instance"), path.get("jobName"), request.getProperty("historyLength"));

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    /** Returns a URI which points to an overview badge for the given application. */
    private HttpResponse badge(String tenant, String application, String instance) {
        URI location = controller.jobController().overviewBadge(ApplicationId.from(tenant, application, instance));
        return redirect(location);
    }

    /** Returns a URI which points to a history badge for the given application and job type. */
    private HttpResponse badge(String tenant, String application, String instance, String jobName, String historyLength) {
        URI location = controller.jobController().historicBadge(ApplicationId.from(tenant, application, instance),
                                                                JobType.fromJobName(jobName),
                                                                historyLength == null ? 5 : Math.min(32, Math.max(0, Integer.parseInt(historyLength))));
        return redirect(location);
    }

    private static HttpResponse redirect(URI location) {
        HttpResponse httpResponse = new HttpResponse(Response.Status.FOUND) {
            @Override public void render(OutputStream outputStream) { }
        };
        httpResponse.headers().add("Location", location.toString());
        return httpResponse;
    }

}
