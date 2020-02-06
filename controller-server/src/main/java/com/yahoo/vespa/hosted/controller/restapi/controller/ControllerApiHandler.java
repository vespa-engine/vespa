// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;

/**
 * This implements the controller/v1 API which provides operators with information about,
 * and control over the Controller.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // Created by injection
public class ControllerApiHandler extends AuditLoggingRequestHandler {

    private final ControllerMaintenance maintenance;
    private final Controller controller;

    public ControllerApiHandler(LoggingRequestHandler.Context parentCtx, Controller controller, ControllerMaintenance maintenance) {
        super(parentCtx, controller.auditLogger());
        this.controller = controller;
        this.maintenance = maintenance;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return get(request);
                case POST: return post(request);
                case DELETE: return delete(request);
                case PATCH: return patch(request);
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
    
    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/")) return root(request);
        if (path.matches("/controller/v1/auditlog/")) return new AuditLogResponse(controller.auditLogger().readLog());
        if (path.matches("/controller/v1/maintenance/")) return new JobsResponse(maintenance.jobControl());
        if (path.matches("/controller/v1/jobs/upgrader")) return new UpgraderResponse(maintenance.upgrader());
        return notFound(path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/maintenance/inactive/{jobName}")) return setActive(path.get("jobName"), false);
        if (path.matches("/controller/v1/jobs/upgrader/confidence/{version}")) return overrideConfidence(request, path.get("version"));
        return notFound(path);
    }

    private HttpResponse delete(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/maintenance/inactive/{jobName}")) return setActive(path.get("jobName"), true);
        if (path.matches("/controller/v1/jobs/upgrader/confidence/{version}")) return removeConfidenceOverride(path.get("version"));
        return notFound(path);
    }

    private HttpResponse patch(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/jobs/upgrader")) return configureUpgrader(request);
        return notFound(path);
    }

    private HttpResponse notFound(Path path) { return ErrorResponse.notFoundError("Nothing at " + path); }

    private HttpResponse root(HttpRequest request) {
        return new ResourceResponse(request, "auditlog", "jobs/upgrader", "maintenance");
    }

    private HttpResponse setActive(String jobName, boolean active) {
        boolean activatingInactiveJob = active && !maintenance.jobControl().isActive(jobName);
        if (!activatingInactiveJob && !maintenance.jobControl().jobs().contains(jobName))
            return ErrorResponse.notFoundError("No job named '" + jobName + "'");
        maintenance.jobControl().setActive(jobName, active);
        return new MessageResponse((active ? "Re-activated" : "Deactivated" ) + " job '" + jobName + "'");
    }

    private HttpResponse configureUpgrader(HttpRequest request) {
        String upgradesPerMinuteField = "upgradesPerMinute";
        String targetMajorVersionField = "targetMajorVersion";

        byte[] jsonBytes = toJsonBytes(request.getData());
        Inspector inspect = SlimeUtils.jsonToSlime(jsonBytes).get();
        Upgrader upgrader = maintenance.upgrader();

        if (inspect.field(upgradesPerMinuteField).valid()) {
            upgrader.setUpgradesPerMinute(inspect.field(upgradesPerMinuteField).asDouble());
        } else if (inspect.field(targetMajorVersionField).valid()) {
            int target = (int)inspect.field(targetMajorVersionField).asLong();
            upgrader.setTargetMajorVersion(Optional.ofNullable(target == 0 ? null : target)); // 0 is the default value
        } else {
            return ErrorResponse.badRequest("No such modifiable field(s)");
        }

        return new UpgraderResponse(maintenance.upgrader());
    }

    private HttpResponse removeConfidenceOverride(String version) {
        maintenance.upgrader().removeConfidenceOverride(Version.fromString(version));
        return new UpgraderResponse(maintenance.upgrader());
    }

    private HttpResponse overrideConfidence(HttpRequest request, String version) {
        Confidence confidence = Confidence.valueOf(asString(request.getData()));
        maintenance.upgrader().overrideConfidence(Version.fromString(version), confidence);
        return new UpgraderResponse(maintenance.upgrader());
    }

    private static String asString(InputStream in) {
        Scanner scanner = new Scanner(in).useDelimiter("\\A");
        if (scanner.hasNext()) {
            return scanner.next();
        }
        return "";
    }

    private static byte[] toJsonBytes(InputStream jsonStream) {
        try {
            return IOUtils.readBytes(jsonStream, 1000 * 1000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
