// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.http.v2.request.HttpConfigRequests;

/**
 * Handler for getting tenant and application for a given hostname.
 *
 * @author hmusum
 */
public class HostHandler extends HttpHandler {
    private final ApplicationRepository applicationRepository;

    @Inject
    public HostHandler(HttpHandler.Context ctx, ApplicationRepository applicationRepository) {
        super(ctx);
        this.applicationRepository = applicationRepository;
    }

    @Override
    public HttpResponse handleGET(HttpRequest request) {
        String hostname = getBindingMatch(request).group(2);
        ApplicationId applicationId = applicationRepository.getApplicationIdForHostname(hostname);
        return (applicationId == null)
                ? createError(hostname)
                : new HostResponse(Response.Status.OK, applicationId, applicationRepository.zone());
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
