// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.http.NotFoundException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Operations on applications (delete, wait for config convergence, restart, application content etc.)
 *
 * @author hmusum
 * @since 5.4
 */
// TODO: Move business logic out of the http layer
public class ApplicationHandler extends HttpHandler {

    private static final String REQUEST_PROPERTY_TIMEOUT = "timeout";

    private final Zone zone;
    private final ApplicationRepository applicationRepository;

    public ApplicationHandler(Executor executor,
                              AccessLog accessLog,
                              Zone zone,
                              ApplicationRepository applicationRepository) {
        super(executor, accessLog);
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
            return applicationRepository.nodeConvergenceCheck(tenant, applicationId, getHostFromRequest(request), request.getUri());
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

        // TODO: Remove this once the config convergence logic is moved to client and is live for all clusters.
        if (isConvergeRequest(request)) {
            try {
                applicationRepository.waitForConfigConverged(tenant, applicationId, new TimeoutBudget(Clock.systemUTC(), durationFromRequestTimeout(request)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (isServiceConvergeListRequest(request)) {
            return applicationRepository.listConfigConvergence(tenant, applicationId, request.getUri());
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

    private Duration durationFromRequestTimeout(HttpRequest request) {
        long timeoutInSeconds = 60;
        if (request.hasProperty(REQUEST_PROPERTY_TIMEOUT)) {
            timeoutInSeconds = Long.parseLong(request.getProperty(REQUEST_PROPERTY_TIMEOUT));
        }
        return Duration.ofSeconds(timeoutInSeconds);
    }

    // Note: Update src/main/resources/configserver-app/services.xml if you do any changes to the bindings
    private static BindingMatch<?> getBindingMatch(HttpRequest request) {
        return HttpConfigRequests.getBindingMatch(request,
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/content/*",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/log",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/restart",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/converge",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/serviceconverge",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/serviceconverge/*",
                "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*",
                "http://*/application/v2/tenant/*/application/*");
    }

    private static boolean isConvergeRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 7 &&
                request.getUri().getPath().endsWith("/converge");
    }

    private static boolean isServiceConvergeListRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 7 &&
                request.getUri().getPath().endsWith("/serviceconverge");
    }

    private static boolean isServiceConvergeRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 8 &&
                request.getUri().getPath().contains("/serviceconverge/");
    }

    private static boolean isContentRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() > 7;
    }

    private static String getHostFromRequest(HttpRequest req) {
        BindingMatch<?> bm = getBindingMatch(req);
        return bm.group(7);
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
        public DeleteApplicationResponse(int status, ApplicationId applicationId) {
            super(status);
            object.setString("message", "Application '" + applicationId + "' deleted");
        }
    }

    private static class GetApplicationResponse extends JSONResponse {
        public GetApplicationResponse(int status, long generation) {
            super(status);
            object.setLong("generation", generation);
        }
    }
}
