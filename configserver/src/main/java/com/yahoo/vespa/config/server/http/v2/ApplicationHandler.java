// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.tenant.Tenant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Operations on applications (delete, wait for config convergence, restart, application content etc.)
 *
 * @author hmusum
 * @since 5.4
 */
public class ApplicationHandler extends HttpHandler {
    private final Zone zone;
    private final ApplicationRepository applicationRepository;

    @Inject
    public ApplicationHandler(HttpHandler.Context ctx,
                              Zone zone,
                              ApplicationRepository applicationRepository) {
        super(ctx);
        this.zone = zone;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public HttpResponse handleDELETE(HttpRequest request) {
        ApplicationId applicationId = getApplicationIdFromRequest(request);
        boolean removed = applicationRepository.remove(applicationId);
        if ( ! removed)
            return HttpErrorResponse.notFoundError("Unable to delete " + applicationId + ": Not found");
        return new DeleteApplicationResponse(Response.Status.OK, applicationId);
    }


    @Override
    public HttpResponse handleGET(HttpRequest request) {
        ApplicationId applicationId = getApplicationIdFromRequest(request);
        Tenant tenant = verifyTenantAndApplication(applicationId);

        if (isServiceConvergeRequest(request)) {
            return applicationRepository.serviceConvergenceCheck(tenant, applicationId, getHostNameFromRequest(request), request.getUri());
        }

        if (isClusterControllerStatusRequest(request)) {
            String hostName = getHostNameFromRequest(request);
            String pathSuffix = getPathSuffix(request);
            return applicationRepository.clusterControllerStatusPage(tenant, applicationId, hostName, pathSuffix);
        }

        if (isContentRequest(request)) {
            long sessionId = applicationRepository.getSessionIdForApplication(tenant, applicationId);
            String contentPath = ApplicationContentRequest.getContentPath(request);
            ApplicationFile applicationFile =
                    applicationRepository.getApplicationFileFromSession(tenant.getName(),
                                                                        sessionId,
                                                                        contentPath,
                                                                        ContentRequest.getApplicationFileMode(request.getMethod()));
            ApplicationContentRequest contentRequest = new ApplicationContentRequest(request,
                                                                                     sessionId,
                                                                                     applicationId,
                                                                                     zone,
                                                                                     contentPath,
                                                                                     applicationFile);
            return new ContentHandler().get(contentRequest);
        }

        if (isServiceConvergeListRequest(request)) {
            return applicationRepository.serviceListToCheckForConfigConvergence(tenant, applicationId, request.getUri());
        }

        if (isFiledistributionStatusRequest(request)) {
            Duration timeout = HttpHandler.getRequestTimeout(request, Duration.ofSeconds(5));
            return applicationRepository.filedistributionStatus(tenant, applicationId, timeout);
        }

        return new GetApplicationResponse(Response.Status.OK, applicationRepository.getApplicationGeneration(tenant, applicationId));
    }

    @Override
    public HttpResponse handlePOST(HttpRequest request) {
        ApplicationId applicationId = getApplicationIdFromRequest(request);
        Tenant tenant = verifyTenantAndApplication(applicationId);
        if (request.getUri().getPath().endsWith("restart"))
            return restart(request, applicationId);
        if (request.getUri().getPath().endsWith("log"))
            return grabLog(request, applicationId, tenant);
        throw new NotFoundException("Illegal POST request '" + request.getUri() + "': Must end by /restart or /log");
    }

    private HttpResponse restart(HttpRequest request, ApplicationId applicationId) {
        if (getBindingMatch(request).groupCount() != 7)
            throw new NotFoundException("Illegal POST restart request '" + request.getUri() +
                                        "': Must have 6 arguments but had " + ( getBindingMatch(request).groupCount()-1 ) );
        applicationRepository.restart(applicationId, hostFilterFrom(request));
        return new JSONResponse(Response.Status.OK); // return empty
    }

    private HttpResponse grabLog(HttpRequest request, ApplicationId applicationId, Tenant tenant) {
        if (getBindingMatch(request).groupCount() != 7)
            throw new NotFoundException("Illegal POST log request '" + request.getUri() +
                    "': Must have 6 arguments but had " + ( getBindingMatch(request).groupCount()-1 ) );
        final String response = applicationRepository.grabLog(tenant, applicationId);
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write(response.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String getContentType() {
                return HttpConfigResponse.JSON_CONTENT_TYPE;
            }
        };
    }

    private HostFilter hostFilterFrom(HttpRequest request) {
        return HostFilter.from(request.getProperty("hostname"),
                               request.getProperty("flavor"),
                               request.getProperty("clusterType"),
                               request.getProperty("clusterId"));
    }

    private Tenant verifyTenantAndApplication(ApplicationId applicationId) {
        try {
            return applicationRepository.verifyTenantAndApplication(applicationId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private static BindingMatch<?> getBindingMatch(HttpRequest request) {
        return HttpConfigRequests.getBindingMatch(request,
                // WARNING: UPDATE src/main/resources/configserver-app/services.xml IF YOU MAKE ANY CHANGES TO THESE BINDINGS!
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/content/*",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/log",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/filedistributionstatus",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/restart",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/serviceconverge",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/serviceconverge/*",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/clustercontroller/*/status/*",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*",
                "http://*/application/v2/tenant/*/application/*");
    }

    private static boolean isServiceConvergeListRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 7 &&
                request.getUri().getPath().endsWith("/serviceconverge");
    }

    private static boolean isServiceConvergeRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 8 &&
                request.getUri().getPath().contains("/serviceconverge/");
    }

    private static boolean isClusterControllerStatusRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 9 &&
                request.getUri().getPath().contains("/clustercontroller/");
    }

    private static boolean isContentRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() > 7 &&
                request.getUri().getPath().contains("/content/");
    }

    private static boolean isFiledistributionStatusRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 7 &&
                request.getUri().getPath().contains("/filedistributionstatus");
    }

    private static String getHostNameFromRequest(HttpRequest req) {
        BindingMatch<?> bm = getBindingMatch(req);
        return bm.group(7);
    }

    private static String getPathSuffix(HttpRequest req) {
        BindingMatch<?> bm = getBindingMatch(req);
        return bm.group(8);
    }

    private static ApplicationId getApplicationIdFromRequest(HttpRequest req) {
        // Two bindings for this: with full app id or only application name
        BindingMatch<?> bm = getBindingMatch(req);
        if (bm.groupCount() > 4) return createFromRequestFullAppId(bm);
        return createFromRequestSimpleAppId(bm);
    }

    // The URL pattern with only tenant and application given
    private static ApplicationId createFromRequestSimpleAppId(BindingMatch<?> bm) {
        TenantName tenant = TenantName.from(bm.group(2));
        ApplicationName application = ApplicationName.from(bm.group(3));
        return new ApplicationId.Builder().tenant(tenant).applicationName(application).build();
    }

    // The URL pattern with full app id given
    private static ApplicationId createFromRequestFullAppId(BindingMatch<?> bm) {
        String tenant = bm.group(2);
        String application = bm.group(3);
        String instance = bm.group(6);
        return new ApplicationId.Builder()
            .tenant(tenant)
            .applicationName(application).instanceName(instance)
            .build();
    }

    private static class DeleteApplicationResponse extends JSONResponse {
        DeleteApplicationResponse(int status, ApplicationId applicationId) {
            super(status);
            object.setString("message", "Application '" + applicationId + "' deleted");
        }
    }

    private static class GetApplicationResponse extends JSONResponse {
        GetApplicationResponse(int status, long generation) {
            super(status);
            object.setLong("generation", generation);
        }
    }
}
