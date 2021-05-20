// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This API serves redirects to a badge server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class BadgeApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(BadgeApiHandler.class.getName());

    private final Controller controller;

    public BadgeApiHandler(Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
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
        Path path = new Path(request.getUri());
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}")) return badge(path.get("tenant"), path.get("application"), path.get("instance"));
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}/{jobName}")) return badge(path.get("tenant"), path.get("application"), path.get("instance"), path.get("jobName"), request.getProperty("historyLength"));

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    /** Returns a URI which points to an overview badge for the given application. */
    private HttpResponse badge(String tenant, String application, String instance) {
        ApplicationId id = ApplicationId.from(tenant, application, instance);
        DeploymentStatus status = controller.jobController().deploymentStatus(controller.applications().requireApplication(TenantAndApplicationId.from(id)));
        Predicate<JobStatus> isDeclaredJob = job -> status.jobSteps().get(job.id()) != null && status.jobSteps().get(job.id()).isDeclared();
        return svgResponse(Badges.overviewBadge(id,
                                                status.jobs().instance(id.instance()).matching(isDeclaredJob),
                                                controller.system()));
    }

    /** Returns a URI which points to a history badge for the given application and job type. */
    private HttpResponse badge(String tenant, String application, String instance, String jobName, String historyLength) {
        ApplicationId id = ApplicationId.from(tenant, application, instance);
        return svgResponse(Badges.historyBadge(id,
                                               controller.jobController().jobStatus(new JobId(id, JobType.fromJobName(jobName))),
                                               historyLength == null ? 5 : Math.min(32, Math.max(0, Integer.parseInt(historyLength)))));
    }

    private static HttpResponse svgResponse(String svg) {
        return new HttpResponse(200) {
            @Override public void render(OutputStream outputStream) throws IOException {
                outputStream.write(svg.getBytes(UTF_8));
            }
            @Override public String getContentType() {
                return "image/svg+xml; charset=UTF-8";
            }
        };
    }

}
