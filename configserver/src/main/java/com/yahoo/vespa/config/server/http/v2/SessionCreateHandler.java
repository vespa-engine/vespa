// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationRepo;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionCreate;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * A handler that is able to create a session from an application package,
 * or create a new session from a previous session (with id or the "active" session).
 * Handles /application/v2/ requests
 *
 * @author hmusum
 * @since 5.1
 */
public class SessionCreateHandler extends SessionHandler {
    private static final Logger log = Logger.getLogger(SessionCreateHandler.class.getName());
    private final Tenants tenants;
    private static final String fromPattern = "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*";
    private final ConfigserverConfig configserverConfig;

    @Inject
    public SessionCreateHandler(Executor executor, AccessLog accessLog, Tenants tenants, ConfigserverConfig configserverConfig) {
        super(executor, accessLog);
        this.tenants = tenants;
        this.configserverConfig = configserverConfig;
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        Slime deployLog = createDeployLog();
        TenantName tenantName = Utils.getTenantFromSessionRequest(request);
        log.log(LogLevel.DEBUG, "Found tenant '" + tenantName + "' in request");
        Tenant tenant = Utils.checkThatTenantExists(tenants, tenantName);
        final SessionCreate sessionCreate = new SessionCreate(tenant.getSessionFactory(), tenant.getLocalSessionRepo(),
                new SessionCreateResponseV2(tenant, deployLog, deployLog.get()));
        TimeoutBudget timeoutBudget = SessionHandler.getTimeoutBudget(request, Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout()));
        if (request.hasProperty("from")) {
            LocalSession fromSession = getExistingSession(tenant, request);
            return sessionCreate.createFromExisting(request, deployLog, fromSession, tenantName, timeoutBudget);
        } else {
            return sessionCreate.create(request, deployLog, tenantName, timeoutBudget);
        }
    }

    private static LocalSession getExistingSession(Tenant tenant, HttpRequest request) {
        ApplicationRepo applicationRepo = tenant.getApplicationRepo();
        LocalSessionRepo localSessionRepo = tenant.getLocalSessionRepo();
        ApplicationId applicationId = getFromProperty(request);
        return SessionHandler.getSessionFromRequest(localSessionRepo, applicationRepo.getSessionIdForApplication(applicationId));
    }

    private static ApplicationId getFromProperty(HttpRequest request) {
        String from = request.getProperty("from");
        if (from == null || "".equals(from)) {
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");
        }
        return getAndValidateFromParameter(URI.create(from));
    }

    private static ApplicationId getAndValidateFromParameter(URI from) {
        UriPattern.Match match = new UriPattern(fromPattern).match(from);
        if (match == null || match.groupCount() < 7) {
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");
        }
        return new ApplicationId.Builder()
            .tenant(match.group(2))
            .applicationName(match.group(3))
            .instanceName(match.group(6)).build();
    }
}
