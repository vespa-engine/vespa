// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;

import java.time.Duration;
import java.time.Instant;

/**
 *  * The implementation of the /application/v2 API.
 *
 *
 * @author hmusum
 */
// TODO: only handles 'prepareandactive' for now, merge other handlers into this one
public class ApplicationApiHandler extends SessionHandler {

    public final static String APPLICATION_X_GZIP = "application/x-gzip";
    public final static String APPLICATION_ZIP = "application/zip";
    public final static String contentTypeHeader = "Content-Type";
    private final Tenants tenants;
    private final Duration zookeeperBarrierTimeout;
    private final Zone zone;

    @Inject
    public ApplicationApiHandler(Context ctx,
                                 ApplicationRepository applicationRepository,
                                 Tenants tenants,
                                 ConfigserverConfig configserverConfig,
                                 Zone zone) {
        super(ctx, applicationRepository);
        this.tenants = tenants;
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        Tenant tenant = getExistingTenant(request);
        TenantName tenantName = tenant.getName();
        long sessionId = getSessionIdV2(request);
        PrepareParams prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);

        PrepareResult result = applicationRepository.prepareAndActivate(tenant, sessionId, prepareParams,
                                                                        shouldIgnoreLockFailure(request),
                                                                        shouldIgnoreSessionStaleFailure(request),
                                                                        Instant.now());
        return new SessionPrepareAndActivateResponse(result, tenantName, request, prepareParams.getApplicationId(), zone);
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        Tenant tenant = getExistingTenant(request);
        TenantName tenantName = tenant.getName();
        PrepareParams prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);
        SessionCreateHandler.validateDataAndHeader(request);

        PrepareResult result =
                applicationRepository.deploy(tenant,
                                             CompressedApplicationInputStream.createFromCompressedStream(request.getData(), request.getHeader(contentTypeHeader)),
                                             prepareParams,
                                             shouldIgnoreLockFailure(request),
                                             shouldIgnoreSessionStaleFailure(request),
                                             Instant.now());
        return new SessionPrepareAndActivateResponse(result, tenantName, request, prepareParams.getApplicationId(), zone);
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
