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

        // This is the main structure that might be part of something bigger later
        Cursor assessment = root.setObject("assessment");

        // Updated gives clue to if the assessement is old
        assessment.setString("updated", "2021-03-12:12:12:12Z");

        // Assessment on the cluster level
        Cursor apps = assessment.setArray("clusters");
        Cursor oneCluster = apps.addObject();
        oneCluster.setString("app", "mytenant:myapp:myinstance");
        oneCluster.setString("zone", "prod.us-east-3");
        oneCluster.setString("cluster", "mycontent");
        oneCluster.setLong("clusterSize", 19);
        oneCluster.setLong("clusterImpact", 3);
        oneCluster.setString("upgradePolicy","10%");
        oneCluster.setDouble("utilizationInWindow", 0.5);
        oneCluster.setString("suggestedAction", "nothing|bcp");
        oneCluster.setString("impact", "low|degraded|outage");

        Cursor groups = oneCluster.setArray("groups");
        Cursor oneGroup = groups.addObject();
        oneGroup.setString("groupId", "23");
        oneGroup.setLong("groupSize", 4);
        oneGroup.setLong("groupImpact", 2);

        // Assessment on the host level
        Cursor hosts = assessment.setArray("hosts");
        Cursor oneHost = hosts.addObject();
        oneHost.setString("hostname", "myhostname");
        oneHost.setString("resources", "vcpu:memory:disk");
        oneHost.setString("state", "active");
        oneHost.setLong("containers", 10);
        oneHost.setString("suggestedAction", "nothing|retire");
        oneHost.setString("impact", "low|degraded|outage"); //For hosts this is mostly decided by request

        return slime;
    }
}