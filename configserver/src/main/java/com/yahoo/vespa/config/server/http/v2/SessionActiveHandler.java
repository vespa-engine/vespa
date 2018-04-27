// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.time.Duration;

import com.google.inject.Inject;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;

/**
 * Handler that activates a session given by tenant and id (PUT).
 *
 * @author vegardh
 * @since 5.1
 */
public class SessionActiveHandler extends SessionHandler {

    private static final Duration DEFAULT_ACTIVATE_TIMEOUT = Duration.ofMinutes(2);

    private final TenantRepository tenantRepository;
    private final Zone zone;

    @Inject
    public SessionActiveHandler(SessionHandler.Context ctx,
                                ApplicationRepository applicationRepository,
                                TenantRepository tenantRepository,
                                Zone zone) {
        super(ctx, applicationRepository);
        this.tenantRepository = tenantRepository;
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        final TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenantRepository, tenantName);
        Tenant tenant = tenantRepository.getTenant(tenantName);
        TimeoutBudget timeoutBudget = getTimeoutBudget(request, DEFAULT_ACTIVATE_TIMEOUT);
        final Long sessionId = getSessionIdV2(request);
        ApplicationId applicationId = applicationRepository.activate(tenant, sessionId, timeoutBudget,
                                                                     shouldIgnoreLockFailure(request),
                                                                     shouldIgnoreSessionStaleFailure(request));
        ApplicationMetaData metaData = applicationRepository.getMetadataFromSession(tenant, sessionId);
        return new SessionActiveResponse(metaData.getSlime(), request, applicationId, sessionId, zone);
    }

    // Overridden to make sure we are logging when this low-level handling of timeout happens
    @Override
    public void handleTimeout(Request request, ResponseHandler responseHandler) {
        log.log(LogLevel.ERROR, "activate timed out for " + request.getUri(), new RuntimeException("activate timed out"));
        super.handleTimeout(request, responseHandler);
    }

}
