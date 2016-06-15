// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.Optional;
import java.util.concurrent.Executor;

import com.google.inject.Inject;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.SessionActiveHandlerBase;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;

/**
 * Handler that activates a session given by tenant and id (PUT).
 *
 * @author vegardh
 * @since 5.1
 */
public class SessionActiveHandler extends SessionActiveHandlerBase {

    private final Tenants tenants;
    private final Optional<Provisioner> hostProvisioner;
    private final Zone zone;

    @Inject
    public SessionActiveHandler(Executor executor,
                                AccessLog accessLog,
                                Tenants tenants,
                                HostProvisionerProvider hostProvisionerProvider,
                                Zone zone) {
        super(executor, accessLog);
        this.tenants = tenants;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TimeoutBudget timeoutBudget = getTimeoutBudget(request, SessionHandler.DEFAULT_ACTIVATE_TIMEOUT);
        TenantName tenantName = Utils.getTenantFromSessionRequest(request);
        log.log(LogLevel.DEBUG, "Found tenant '" + tenantName + "' in request");
        Tenant tenant = Utils.checkThatTenantExists(tenants, tenantName);
        LocalSession localSession = getSessionFromRequestV2(tenant.getLocalSessionRepo(), request);
        activate(request, tenant.getLocalSessionRepo(), tenant.getActivateLock(), timeoutBudget, hostProvisioner, localSession);
        return new SessionActiveResponse(localSession.getMetaData().getSlime(), tenantName, request, localSession, zone);
    }

}
