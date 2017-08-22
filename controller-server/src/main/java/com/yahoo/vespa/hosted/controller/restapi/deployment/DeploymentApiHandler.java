// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Uri;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * This implements the deployment/v1 API which provides information about the status of Vespa platform and
 * application deployments.
 * 
 * @author bratseth
 */
public class DeploymentApiHandler extends LoggingRequestHandler {

    private final Controller controller;

    public DeploymentApiHandler(Executor executor, AccessLog accessLog, Controller controller) {
        super(executor, accessLog);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
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
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/deployment/v1/")) return root(request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,OPTIONS");
        return response;
    }
    
    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor platformArray = root.setArray("versions");
        for (VespaVersion version : controller.versionStatus().versions()) {
            Cursor versionObject = platformArray.addObject();
            versionObject.setString("version", version.versionNumber().toString());
            versionObject.setString("confidence", version.confidence().name());
            versionObject.setString("commit", version.releaseCommit());
            versionObject.setLong("date", version.releasedAt().toEpochMilli());
            versionObject.setBool("controllerVersion", version.isSelfVersion());
            versionObject.setBool("systemVersion", version.isCurrentSystemVersion());
            
            Cursor configServerArray = versionObject.setArray("configServers");
            for (String configServerHostnames : version.configServerHostnames()) {
                Cursor configServerObject = configServerArray.addObject();
                configServerObject.setString("hostname", configServerHostnames);
            }

            Cursor failingArray = versionObject.setArray("failingApplications");
            for (ApplicationId id : version.statistics().failing()) {
                Optional<Application> application = controller.applications().get(id);
                if ( ! application.isPresent()) continue; // deleted just now

                Instant failingSince = application.get().deploymentJobs().failingSince();
                if (failingSince == null) continue; // started working just now

                Cursor applicationObject = failingArray.addObject();
                toSlime(id, applicationObject, request);
                applicationObject.setLong("failingSince", failingSince.toEpochMilli());
            }

            Cursor productionArray = versionObject.setArray("productionApplications");
            for (ApplicationId id : version.statistics().production())
                toSlime(id, productionArray.addObject(), request);
        }
        return new SlimeJsonResponse(slime);
    }
    
    private void toSlime(ApplicationId id, Cursor object, HttpRequest request) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
        object.setString("url", new Uri(request.getUri()).withPath("/application/v4" +
                                                                   "/tenant/" + id.tenant().value() +
                                                                   "/application/" + id.application().value())
                                                         .toString());
    }
    
}
