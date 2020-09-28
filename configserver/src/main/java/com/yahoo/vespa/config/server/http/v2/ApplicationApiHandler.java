// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.time.Duration;

import static com.yahoo.vespa.config.server.application.CompressedApplicationInputStream.createFromCompressedStream;
import static com.yahoo.vespa.config.server.http.Utils.checkThatTenantExists;
import static com.yahoo.vespa.config.server.http.v2.SessionCreateHandler.validateDataAndHeader;

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
    private final TenantRepository tenantRepository;
    private final Duration zookeeperBarrierTimeout;
    private final Zone zone;

    @Inject
    public ApplicationApiHandler(Context ctx,
                                 ApplicationRepository applicationRepository,
                                 ConfigserverConfig configserverConfig,
                                 Zone zone) {
        super(ctx, applicationRepository);
        this.tenantRepository = applicationRepository.tenantRepository();
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        validateDataAndHeader(request);
        TenantName tenantName = validateTenant(request);
        PrepareParams prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);
        CompressedApplicationInputStream compressedStream = createFromCompressedStream(request.getData(), request.getHeader(contentTypeHeader));
        PrepareResult result = applicationRepository.deploy(compressedStream, prepareParams);
        return new SessionPrepareAndActivateResponse(result, request, prepareParams.getApplicationId(), zone);
    }

    @Override
    public Duration getTimeout() {
        return zookeeperBarrierTimeout.plus(Duration.ofSeconds(10));
    }

    private TenantName validateTenant(HttpRequest request) {
        TenantName tenantName = getTenantNameFromRequest(request);
        checkThatTenantExists(tenantRepository, tenantName);
        return tenantName;
    }

    public static TenantName getTenantNameFromRequest(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, "http://*/application/v2/tenant/*/prepareandactivate*");
        return TenantName.from(bm.group(2));
    }

}
