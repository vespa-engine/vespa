// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.time.Duration;
import java.util.concurrent.Executor;

import com.google.inject.Inject;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
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

    private final Tenants tenants;
    private final Zone zone;

    @Inject
    public SessionActiveHandler(Executor executor,
                                AccessLog accessLog,
                                Tenants tenants,
                                Zone zone,
                                ApplicationRepository applicationRepository) {
        super(executor, accessLog, applicationRepository);
        this.tenants = tenants;
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        final TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenants, tenantName);
        Tenant tenant = tenants.getTenant(tenantName);
        TimeoutBudget timeoutBudget = getTimeoutBudget(request, DEFAULT_ACTIVATE_TIMEOUT);
        final Long sessionId = getSessionIdV2(request);
        ApplicationId applicationId = applicationRepository.activate(tenant, sessionId, timeoutBudget,
                                                                     shouldIgnoreLockFailure(request),
                                                                     shouldIgnoreSessionStaleFailure(request));
        ApplicationMetaData metaData = applicationRepository.getMetadataFromSession(tenant, sessionId);
        return new SessionActiveResponse(metaData.getSlime(), request, applicationId, sessionId, zone);
    }

    private boolean shouldIgnoreLockFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

    /**
     * True if this request should ignore activation failure because the session was made from an active session that is not active now
     * @param request a {@link com.yahoo.container.jdisc.HttpRequest}
     * @return true if ignore failure
     */
    private boolean shouldIgnoreSessionStaleFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

}
