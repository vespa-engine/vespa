// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.concurrent.Executor;

import com.google.inject.Inject;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.SessionActiveHandlerBase;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.LocalSession;

/**
 * Handler that activates a session given by tenant and id (PUT).
 *
 * @author vegardh
 * @since 5.1
 */
public class SessionActiveHandler extends SessionActiveHandlerBase {

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
        TimeoutBudget timeoutBudget = getTimeoutBudget(request, SessionHandler.DEFAULT_ACTIVATE_TIMEOUT);
        final TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenants, tenantName);
        Tenant tenant = tenants.getTenant(tenantName);
        LocalSession localSession = applicationRepository.getLocalSession(tenant, getSessionIdV2(request));
        activate(request, tenant.getLocalSessionRepo(), tenant.getActivateLock(), timeoutBudget, localSession);
        return new SessionActiveResponse(localSession.getMetaData().getSlime(), tenantName, request, localSession, zone);
    }

}
