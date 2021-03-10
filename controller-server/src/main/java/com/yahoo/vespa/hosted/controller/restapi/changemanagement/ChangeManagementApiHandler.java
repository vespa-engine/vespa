package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.yolean.Exceptions;

import java.util.logging.Level;

public class ChangeManagementApiHandler extends AuditLoggingRequestHandler {

    private final Controller controller;

    public ChangeManagementApiHandler(LoggingRequestHandler.Context ctx, Controller controller) {
        super(ctx, controller.auditLogger());
        this.controller = controller;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case POST:
                    return post(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/assessment")) return new SlimeJsonResponse(assessment());
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private Slime assessment() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor assessment = root.setObject("assessment");
        assessment.setArray("applications");
        assessment.setArray("hosts");
        return slime;
    }
}