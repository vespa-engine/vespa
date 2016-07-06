// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.ApplicationSet;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.Tenants;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.RefeedActions;
import com.yahoo.vespa.config.server.configchange.RefeedActionsFormatter;
import com.yahoo.vespa.config.server.configchange.RestartActionsFormatter;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.*;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A handler that prepares a session given by an id in the request. v2 of application API
 *
 * @author hmusum
 * @since 5.1.29
 */
// TODO: Move business logic out of the HTTP layer and delegate to a Deployer
public class SessionPrepareHandler extends SessionHandler {

    private static final Logger log = Logger.getLogger(SessionPrepareHandler.class.getName());

    private final Tenants tenants;
    private final ConfigserverConfig configserverConfig;

    @Inject
    public SessionPrepareHandler(Executor executor,
                                 AccessLog accessLog,
                                 Tenants tenants,
                                 ConfigserverConfig configserverConfig) {
        super(executor, accessLog);
        this.tenants = tenants;
        this.configserverConfig = configserverConfig;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TenantName tenantName = Utils.getTenantFromSessionRequest(request);
        Tenant tenantContext = Utils.checkThatTenantExists(tenants, tenantName);
        LocalSession session = getSessionFromRequestV2(tenantContext.getLocalSessionRepo(), request);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalArgumentException("Session is active: " + session.getSessionId());
        }
        log.log(LogLevel.DEBUG, "session=" + session);
        boolean verbose = request.getBooleanProperty("verbose");
        Slime rawDeployLog = createDeployLog();
        PrepareParams prepParams = PrepareParams.fromHttpRequest(request, tenantName, configserverConfig);
        // An app id currently using only the name
        ApplicationId appId = prepParams.getApplicationId();
        DeployLogger logger = createLogger(rawDeployLog, verbose, appId);
        ConfigChangeActions actions = session.prepare(logger, prepParams, getCurrentActiveApplicationSet(tenantContext, appId), tenantContext.getPath());
        logConfigChangeActions(actions, logger);
        log.log(LogLevel.INFO, Tenants.logPre(appId)+"Session "+session.getSessionId()+" prepared successfully. ");
        return new SessionPrepareResponse(rawDeployLog, tenantContext, request, session, actions);
    }

    private static void logConfigChangeActions(ConfigChangeActions actions, DeployLogger logger) {
        if ( ! actions.getRestartActions().getEntries().isEmpty()) {
            logger.log(Level.WARNING, "Change(s) between active and new application that require restart:\n" +
                                      new RestartActionsFormatter(actions.getRestartActions()).format());
        }
        if ( ! actions.getRefeedActions().getEntries().isEmpty()) {
            boolean allAllowed = actions.getRefeedActions().getEntries().stream().allMatch(RefeedActions.Entry::allowed);
            logger.log(allAllowed ? Level.INFO : Level.WARNING,
                       "Change(s) between active and new application that may require re-feed:\n" +
                       new RefeedActionsFormatter(actions.getRefeedActions()).format());
        }
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        TenantName tenantName = Utils.getTenantFromSessionRequest(request);
        log.log(LogLevel.DEBUG, "Found tenant '" + tenantName + "' in request");
        Tenant tenant = Utils.checkThatTenantExists(tenants, tenantName);
        RemoteSession session = getSessionFromRequestV2(tenant.getRemoteSessionRepo(), request);
        if (Session.Status.ACTIVATE.equals(session.getStatus()))
            throw new IllegalArgumentException("Session is active: " + session.getSessionId());
        if (!Session.Status.PREPARE.equals(session.getStatus()))
            throw new IllegalArgumentException("Session not prepared: " + session.getSessionId());
        return new SessionPrepareResponse(createDeployLog(), tenant, request, session, new ConfigChangeActions());
    }

    private static Optional<ApplicationSet> getCurrentActiveApplicationSet(Tenant tenant, ApplicationId appId) {
        Optional<ApplicationSet> currentActiveApplicationSet = Optional.empty();
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        try {
            long currentActiveSessionId = applicationRepo.getSessionIdForApplication(appId);
            final RemoteSession currentActiveSession = tenant.getRemoteSessionRepo().getSession(currentActiveSessionId);
            if (currentActiveSession != null) {
                currentActiveApplicationSet = Optional.ofNullable(currentActiveSession.ensureApplicationLoaded());
            }
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
        }
        return currentActiveApplicationSet;
    }
}
