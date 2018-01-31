// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.TimeoutBudget;

import java.time.Clock;
import java.time.Duration;

/**
 * Super class for session handlers, that takes care of checking valid
 * methods for a request. Session handlers should subclass this method and
 * implement the handleMETHOD methods that it supports.
 *
 * @author hmusum
 */
public class SessionHandler extends HttpHandler {

    protected final ApplicationRepository applicationRepository;

    public SessionHandler(HttpHandler.Context ctx, ApplicationRepository applicationRepository)
    {
        super(ctx);
        this.applicationRepository = applicationRepository;
    }

    /**
     * Gets the raw session id from request (v2). Input request must have a valid path.
     *
     * @param request a request
     * @return a session id
     */
    public static String getRawSessionIdV2(HttpRequest request) {
        final String path = request.getUri().toString();
        BindingMatch<?> bm = Utils.getBindingMatch(request, "http://*/application/v2/tenant/*/session/*/*");
        if (bm.groupCount() < 4) {
            // This would mean the subtype of this doesn't have the correct binding
            throw new IllegalArgumentException("Can not get session id from request '" + path + "'");
        }
        return bm.group(3);
    }

    /**
     * Gets session id (as a number) from request (v2). Input request must have a valid path.
     *
     * @param request a request
     * @return a session id
     */
    public static Long getSessionIdV2(HttpRequest request) {
        try {
            return Long.parseLong(getRawSessionIdV2(request));
        } catch (NumberFormatException e) {
            throw createSessionException(request);
        }
    }

    private static BadRequestException createSessionException(HttpRequest request) {
        return new BadRequestException("Session id in request is not a number, request was '" +
                request.getUri().toString() + "'");
    }

    public static TimeoutBudget getTimeoutBudget(HttpRequest request, Duration defaultTimeout) {
        return new TimeoutBudget(Clock.systemUTC(), getRequestTimeout(request, defaultTimeout));
    }

    public static DeployHandlerLogger createLogger(Slime deployLog, HttpRequest request, ApplicationId app) {
        return createLogger(deployLog, request.getBooleanProperty("verbose"), app);
    }

    public static DeployHandlerLogger createLogger(Slime deployLog, boolean verbose, ApplicationId app) {
        return new DeployHandlerLogger(deployLog.get().setArray("log"), verbose, app);
    }

    // TODO: Refactor to use the one in ApplicationRepository and remove
    protected Slime createDeployLog() {
        Slime deployLog = new Slime();
        deployLog.setObject();
        return deployLog;
    }

    protected static boolean shouldIgnoreLockFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

    /**
     * True if this request should ignore activation failure because the session was made from an active session that is not active now
     * @param request a {@link com.yahoo.container.jdisc.HttpRequest}
     * @return true if ignore failure
     */
    protected static boolean shouldIgnoreSessionStaleFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

}
