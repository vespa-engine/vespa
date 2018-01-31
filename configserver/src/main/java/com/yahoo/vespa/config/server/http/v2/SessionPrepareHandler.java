// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;

import java.time.Duration;
import java.time.Instant;

/**
 * A handler that prepares a session given by an id in the request. v2 of application API
 *
 * @author hmusum
 */
public class SessionPrepareHandler extends SessionHandler {

    private final Tenants tenants;
    private final Duration zookeeperBarrierTimeout;

    @Inject
    public SessionPrepareHandler(SessionHandler.Context ctx,
                                 ApplicationRepository applicationRepository,
                                 Tenants tenants,
                                 ConfigserverConfig configserverConfig) {
        super(ctx, applicationRepository);
        this.tenants = tenants;
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
    }

  @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        Tenant tenant = getExistingTenant(request);
        TenantName tenantName = tenant.getName();
        long sessionId = getSessionIdV2(request);
        PrepareParams prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);
        PrepareResult result = applicationRepository.prepare(tenant, sessionId, prepareParams, Instant.now());
        return new SessionPrepareResponse(result, tenantName, request);
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        Tenant tenant = getExistingTenant(request);
        long sessionId = getSessionIdV2(request);
        applicationRepository.validateThatRemoteSessionIsNotActive(tenant, sessionId);
        applicationRepository.validateThatRemoteSessionIsPrepared(tenant, sessionId);
        return new SessionPrepareResponse(createDeployLog(), tenant.getName(), request, sessionId);
    }

    @Override
    public Duration getTimeout() {
        return zookeeperBarrierTimeout.plus(Duration.ofSeconds(10));
    }

    private Tenant getExistingTenant(HttpRequest request) {
        TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenants, tenantName);
        return tenants.getTenant(tenantName);
    }
}
