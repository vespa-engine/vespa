// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.RemoteSessionRepo;
import com.yahoo.vespa.curator.Curator;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Handler for deleting a currently active application for a tenant.
 *
 * @author hmusum
 * @since 5.4
 */
// TODO: Remove business logic out of the http layer
public class ApplicationHandler extends HttpHandler {

    private static final String REQUEST_PROPERTY_TIMEOUT = "timeout";
    private final Tenants tenants;
    private final ContentHandler contentHandler = new ContentHandler();
    private final Optional<Provisioner> hostProvisioner;
    private final ApplicationConvergenceChecker convergeChecker;
    private final Zone zone;
    private final LogServerLogGrabber logServerLogGrabber;
    private final ApplicationRepository applicationRepository;

    public ApplicationHandler(Executor executor, AccessLog accessLog, Tenants tenants,
                              HostProvisionerProvider hostProvisionerProvider, Zone zone,
                              ApplicationConvergenceChecker convergeChecker,
                              LogServerLogGrabber logServerLogGrabber,
                              ConfigserverConfig configserverConfig, Curator curator) {
        super(executor, accessLog);
        this.tenants = tenants;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.zone = zone;
        this.convergeChecker = convergeChecker;
        this.logServerLogGrabber = logServerLogGrabber;
        this.applicationRepository = new ApplicationRepository(tenants, hostProvisionerProvider, configserverConfig, curator);
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
            Application application = getApplication(tenant, applicationId);
            return convergeChecker.nodeConvergenceCheck(application, getHostFromRequest(request), request.getUri());
        }
        if (isContentRequest(request)) {
            LocalSession session = SessionHandler.getSessionFromRequest(tenant.getLocalSessionRepo(), tenant.getApplicationRepo().getSessionIdForApplication(applicationId));
            return contentHandler.get(ApplicationContentRequest.create(request, session, applicationId, zone));
        }
        Application application = getApplication(tenant, applicationId);

        // TODO: Remove this once the config convergence logic is moved to client and is live for all clusters.
        if (isConvergeRequest(request)) {
            try {
                convergeChecker.waitForConfigConverged(application, new TimeoutBudget(Clock.systemUTC(), durationFromRequestTimeout(request)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (isServiceConvergeListRequest(request)) {
             return convergeChecker.listConfigConvergence(application, request.getUri());
        }
        return new GetApplicationResponse(Response.Status.OK, application.getApplicationGeneration());
    }

    @Override
    public HttpResponse handlePOST(HttpRequest request) {
        ApplicationId applicationId = getApplicationIdFromRequest(request);
        Tenant tenant = verifyTenantAndApplication(applicationId);
        if (request.getUri().getPath().endsWith("restart"))
            return handlePostRestart(request, applicationId);
        if (request.getUri().getPath().endsWith("log"))
            return handlePostLog(request, applicationId, tenant);
        throw new NotFoundException("Illegal POST request '" + request.getUri() + "': Must end by /restart or /log");
    }

    private HttpResponse handlePostRestart(HttpRequest request, ApplicationId applicationId) {
        if (getBindingMatch(request).groupCount() != 7)
            throw new NotFoundException("Illegal POST restart request '" + request.getUri() +
                                        "': Must have 6 arguments but had " + ( getBindingMatch(request).groupCount()-1 ) );
        if (hostProvisioner.isPresent())
            hostProvisioner.get().restart(applicationId, hostFilterFrom(request));
        return new JSONResponse(Response.Status.OK); // return empty
    }

    private HttpResponse handlePostLog(HttpRequest request, ApplicationId applicationId, Tenant tenant) {
        if (getBindingMatch(request).groupCount() != 7)
            throw new NotFoundException("Illegal POST log request '" + request.getUri() +
                    "': Must have 6 arguments but had " + ( getBindingMatch(request).groupCount()-1 ) );
        Application application = getApplication(tenant, applicationId);
        return logServerLogGrabber.grabLog(application);
    }

    private HostFilter hostFilterFrom(HttpRequest request) {
        return HostFilter.from(request.getProperty("hostname"),
                               request.getProperty("flavor"),
                               request.getProperty("clusterType"),
                               request.getProperty("clusterId"));
    }

    private Tenant verifyTenantAndApplication(ApplicationId applicationId) {
        Tenant tenant = Utils.checkThatTenantExists(tenants, applicationId.tenant());
        List<ApplicationId> applicationIds = listApplicationIds(tenant);
        if ( ! applicationIds.contains(applicationId)) {
            throw new NotFoundException("No such application id: " + applicationId);
        }
        return tenant;
    }

    private Duration durationFromRequestTimeout(HttpRequest request) {
        long timeoutInSeconds = 60;
        if (request.hasProperty(REQUEST_PROPERTY_TIMEOUT)) {
            timeoutInSeconds = Long.parseLong(request.getProperty(REQUEST_PROPERTY_TIMEOUT));
        }
        return Duration.ofSeconds(timeoutInSeconds);
    }

    private Application getApplication(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        RemoteSessionRepo remoteSessionRepo = tenant.getRemoteSessionRepo();
        long sessionId = applicationRepo.getSessionIdForApplication(applicationId);
        RemoteSession session = remoteSessionRepo.getSession(sessionId, 0);
        return session.ensureApplicationLoaded().getForVersionOrLatest(Optional.empty());
    }

    private List<ApplicationId> listApplicationIds(Tenant tenant) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return applicationRepo.listApplications();
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
                request.getUri().getPath().endsWith("converge");
    }

    private static boolean isServiceConvergeListRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 7 &&
                request.getUri().getPath().endsWith("serviceconverge");
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
