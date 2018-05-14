// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;

import java.net.URI;
import java.time.Duration;

/**
 * A handler that is able to create a session from an application package,
 * or create a new session from a previous session (with id or the "active" session).
 * Handles /application/v2/ requests
 *
 * @author hmusum
 */
public class SessionCreateHandler extends SessionHandler {

    private static final String fromPattern = "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*";

    private final TenantRepository tenantRepository;
    private final Duration zookeeperBarrierTimeout;

    @Inject
    public SessionCreateHandler(SessionHandler.Context ctx,
                                ApplicationRepository applicationRepository,
                                TenantRepository tenantRepository,
                                ConfigserverConfig configserverConfig) {
        super(ctx, applicationRepository);
        this.tenantRepository = tenantRepository;
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        Slime deployLog = applicationRepository.createDeployLog();
        final TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenantRepository, tenantName);
        TimeoutBudget timeoutBudget = SessionHandler.getTimeoutBudget(request, zookeeperBarrierTimeout);
        DeployLogger logger = createLogger(request, deployLog, tenantName);
        long sessionId;
        if (request.hasProperty("from")) {
            ApplicationId applicationId = getFromApplicationId(request);
            sessionId = applicationRepository.createSessionFromExisting(applicationId, logger, timeoutBudget);
        } else {
            validateDataAndHeader(request);
            String name = getNameProperty(request, logger);
            // TODO: we are always using instance name 'default' here, fix
            ApplicationId applicationId = ApplicationId.from(tenantName, ApplicationName.from(name), InstanceName.defaultName());
            sessionId = applicationRepository.createSession(applicationId, timeoutBudget, request.getData(), request.getHeader(ApplicationApiHandler.contentTypeHeader));
        }
        return createResponse(request, tenantName, deployLog, sessionId);
    }

    private static ApplicationId getFromApplicationId(HttpRequest request) {
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

    private static DeployHandlerLogger createLogger(HttpRequest request, Slime deployLog, TenantName tenant) {
        return SessionHandler.createLogger(deployLog, request,
                                           new ApplicationId.Builder().tenant(tenant).applicationName("-").build());
    }

    private static String getNameProperty(HttpRequest request, DeployLogger logger) {
        String name = request.getProperty("name");
        // TODO: Do we need validation of this parameter?
        if (name == null) {
            name = "default";
            logger.log(LogLevel.INFO, "No application name given, using '" + name + "'");
        }
        return name;
    }

    static void validateDataAndHeader(HttpRequest request) {
        if (request.getData() == null) {
            throw new BadRequestException("Request contains no data");
        }
        String header = request.getHeader(ApplicationApiHandler.contentTypeHeader);
        if (header == null) {
            throw new BadRequestException("Request contains no " + ApplicationApiHandler.contentTypeHeader + " header");
        } else if (!(header.equals(ApplicationApiHandler.APPLICATION_X_GZIP) || header.equals(ApplicationApiHandler.APPLICATION_ZIP))) {
            throw new BadRequestException("Request contains invalid " + ApplicationApiHandler.contentTypeHeader + " header, only '" +
                                                  ApplicationApiHandler.APPLICATION_X_GZIP + "' and '" + ApplicationApiHandler.APPLICATION_ZIP + "' are supported");
        }
    }

    private HttpResponse createResponse(HttpRequest request, TenantName tenantName, Slime deployLog, long sessionId) {
        return new SessionCreateResponse(tenantName, deployLog, deployLog.get())
                .createResponse(request.getHost(), request.getPort(), sessionId);
    }
}
