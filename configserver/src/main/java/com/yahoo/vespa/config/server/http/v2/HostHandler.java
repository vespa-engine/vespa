// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.*;

import java.util.concurrent.Executor;


/**
 * Handler for getting tenant and application for a given hostname.
 *
 * @author hmusum
 * @since 5.19
 */
public class HostHandler extends HttpHandler {
    final HostRegistries hostRegistries;
    private final Zone zone;

    @Inject
    public HostHandler(HttpHandler.Context ctx,
                       GlobalComponentRegistry globalComponentRegistry) {
        super(ctx);
        this.hostRegistries = globalComponentRegistry.getHostRegistries();
        this.zone = globalComponentRegistry.getZone();
    }

    @Override
    public HttpResponse handleGET(HttpRequest request) {
        String hostname = getBindingMatch(request).group(2);
        log.log(LogLevel.DEBUG, "hostname=" + hostname);

        HostRegistry<TenantName> tenantHostRegistry = hostRegistries.getTenantHostRegistry();
        log.log(LogLevel.DEBUG, "hosts in tenant host registry '" + tenantHostRegistry + "' " + tenantHostRegistry.getAllHosts());
        TenantName tenant = tenantHostRegistry.getKeyForHost(hostname);
        if (tenant == null) return createError(hostname);
        log.log(LogLevel.DEBUG, "tenant=" + tenant);
        HostRegistry<ApplicationId> applicationIdHostRegistry = hostRegistries.getApplicationHostRegistry(tenant);
        ApplicationId applicationId;
        if (applicationIdHostRegistry == null) return createError(hostname);
        applicationId = applicationIdHostRegistry.getKeyForHost(hostname);
        log.log(LogLevel.DEBUG, "applicationId=" + applicationId);
        if (applicationId == null) {
            return createError(hostname);
        } else {
            log.log(LogLevel.DEBUG, "hosts in application host registry '" + applicationIdHostRegistry + "' " + applicationIdHostRegistry.getAllHosts());
            return new HostResponse(Response.Status.OK, applicationId, zone);
        }
    }

    private HttpErrorResponse createError(String hostname) {
        return HttpErrorResponse.notFoundError("Could not find any application using host '" + hostname + "'");
    }

    private static BindingMatch<?> getBindingMatch(HttpRequest request) {
        return HttpConfigRequests.getBindingMatch(request, "http://*/application/v2/host/*");
    }

    private static class HostResponse extends JSONResponse {
        public HostResponse(int status, ApplicationId applicationId, Zone zone) {
            super(status);
            object.setString("tenant", applicationId.tenant().value());
            object.setString("application", applicationId.application().value());
            object.setString("environment", zone.environment().value());
            object.setString("region", zone.region().value());
            object.setString("instance", applicationId.instance().value());
        }
    }
}
