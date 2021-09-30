// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.Response;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.http.v2.request.ApplicationContentRequest;
import com.yahoo.vespa.config.server.http.v2.response.ApplicationSuspendedResponse;
import com.yahoo.vespa.config.server.http.v2.response.DeleteApplicationResponse;
import com.yahoo.vespa.config.server.http.v2.response.GetApplicationResponse;
import com.yahoo.vespa.config.server.http.v2.response.QuotaUsageResponse;
import com.yahoo.vespa.config.server.http.v2.response.ReindexingResponse;
import com.yahoo.vespa.config.server.tenant.Tenant;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Operations on applications (delete, wait for config convergence, restart, application content etc.)
 *
 * @author hmusum
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
    public HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());

        if (path.matches("/application/v2/tenant/{tenant}/application/{application}")) return getApplicationResponse(ApplicationId.from(path.get("tenant"), path.get("application"), InstanceName.defaultName().value()));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}")) return getApplicationResponse(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/content/{*}")) return content(applicationId(path), path.getRest(), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/filedistributionstatus")) return filedistributionStatus(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/logs")) return logs(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/metrics/deployment")) return deploymentMetrics(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/metrics/proton")) return protonMetrics(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindexing")) return getReindexingStatus(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/service/{service}/{hostname}/status/{*}")) return serviceStatusPage(applicationId(path), path.get("service"), path.get("hostname"), path.getRest());
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/serviceconverge")) return listServiceConverge(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/serviceconverge/{hostAndPort}")) return checkServiceConverge(applicationId(path), path.get("hostAndPort"), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/suspended")) return isSuspended(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/tester/{command}")) return testerRequest(applicationId(path), path.get("command"), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/quota")) return quotaUsage(applicationId(path));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    @Override
    public HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());

        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindex")) return triggerReindexing(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindexing")) return enableReindexing(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/restart")) return restart(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/tester/run/{suite}")) return testerStartTests(applicationId(path), path.get("suite"), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/validate-secret-store")) return validateSecretStore(applicationId(path), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    @Override
    public HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());

        if (path.matches("/application/v2/tenant/{tenant}/application/{application}")) return deleteApplication(ApplicationId.from(path.get("tenant"), path.get("application"), InstanceName.defaultName().value()));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}")) return deleteApplication(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindexing")) return disableReindexing(applicationId(path));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse listServiceConverge(ApplicationId applicationId, HttpRequest request) {
        return applicationRepository.servicesToCheckForConfigConvergence(applicationId, request.getUri(),
                getTimeoutFromRequest(request), getVespaVersionFromRequest(request));
    }

    private HttpResponse checkServiceConverge(ApplicationId applicationId, String hostAndPort, HttpRequest request) {
        return applicationRepository.checkServiceForConfigConvergence(applicationId, hostAndPort, request.getUri(),
                getTimeoutFromRequest(request), getVespaVersionFromRequest(request));
    }

    private HttpResponse serviceStatusPage(ApplicationId applicationId, String service, String hostname, String pathSuffix) {
        return applicationRepository.serviceStatusPage(applicationId, hostname, service, pathSuffix);
    }

    private HttpResponse content(ApplicationId applicationId, String contentPath, HttpRequest request) {
        long sessionId = applicationRepository.getSessionIdForApplication(applicationId);
        ApplicationFile applicationFile =
                applicationRepository.getApplicationFileFromSession(applicationId.tenant(),
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

    private HttpResponse filedistributionStatus(ApplicationId applicationId, HttpRequest request) {
        return applicationRepository.filedistributionStatus(applicationId, getTimeoutFromRequest(request));
    }

    private HttpResponse logs(ApplicationId applicationId, HttpRequest request) {
        Optional<String> hostname = Optional.ofNullable(request.getProperty("hostname"));
        String apiParams = Optional.ofNullable(request.getUri().getQuery()).map(q -> "?" + q).orElse("");
        return applicationRepository.getLogs(applicationId, hostname, apiParams);
    }

    private HttpResponse protonMetrics(ApplicationId applicationId) {
        return applicationRepository.getProtonMetrics(applicationId);
    }

    private HttpResponse deploymentMetrics(ApplicationId applicationId) {
        return applicationRepository.getDeploymentMetrics(applicationId);
    }

    private HttpResponse isSuspended(ApplicationId applicationId) {
        return new ApplicationSuspendedResponse(applicationRepository.isSuspended(applicationId));
    }

    private HttpResponse testerRequest(ApplicationId applicationId, String command, HttpRequest request) {
        switch (command) {
            case "status":
                return applicationRepository.getTesterStatus(applicationId);
            case "log":
                Long after = Long.valueOf(request.getProperty("after"));
                return applicationRepository.getTesterLog(applicationId, after);
            case "ready":
                return applicationRepository.isTesterReady(applicationId);
            case "report":
                return applicationRepository.getTestReport(applicationId);
            default:
                throw new IllegalArgumentException("Unknown tester command in request " + request.getUri().toString());
        }
    }

    private HttpResponse quotaUsage(ApplicationId applicationId) {
        double quotaUsageRate = applicationRepository.getQuotaUsageRate(applicationId);
        return new QuotaUsageResponse(quotaUsageRate);
    }

    private HttpResponse getApplicationResponse(ApplicationId applicationId) {
        return new GetApplicationResponse(Response.Status.OK,
                applicationRepository.getApplicationGeneration(applicationId),
                applicationRepository.getAllVersions(applicationId),
                applicationRepository.getApplicationPackageReference(applicationId));
    }

    public HttpResponse deleteApplication(ApplicationId applicationId) {
        if (applicationRepository.delete(applicationId))
            return new DeleteApplicationResponse(applicationId);
        return ErrorResponse.notFoundError("Unable to delete " + applicationId.toFullString() + ": Not found");
    }


    private Model getActiveModelOrThrow(ApplicationId id) {
        return applicationRepository.getActiveApplicationSet(id)
                                    .orElseThrow(() -> new NotFoundException("Application '" + id + "' not found"))
                                    .getForVersionOrLatest(Optional.empty(), applicationRepository.clock().instant())
                .getModel();
    }

    private HttpResponse triggerReindexing(ApplicationId applicationId, HttpRequest request) {
        Model model = getActiveModelOrThrow(applicationId);
        Map<String, Set<String>> documentTypes = model.documentTypesByCluster();
        Map<String, Set<String>> indexedDocumentTypes = model.indexedDocumentTypesByCluster();

        boolean indexedOnly = request.getBooleanProperty("indexedOnly");
        Set<String> clusters = StringUtilities.split(request.getProperty("clusterId"));
        Set<String> types = StringUtilities.split(request.getProperty("documentType"));

        Map<String, Set<String>> reindexed = new TreeMap<>();
        Instant now = applicationRepository.clock().instant();
        applicationRepository.modifyReindexing(applicationId, reindexing -> {
            for (String cluster : clusters.isEmpty() ? documentTypes.keySet() : clusters) {
                if ( ! documentTypes.containsKey(cluster))
                    throw new IllegalArgumentException("No content cluster '" + cluster + "' in application — only: " +
                                                       String.join(", ", documentTypes.keySet()));

                for (String type : types.isEmpty() ? documentTypes.get(cluster) : types) {
                    if ( ! documentTypes.get(cluster).contains(type))
                        throw new IllegalArgumentException("No document type '" + type + "' in cluster '" + cluster + "' — only: " +
                                                           String.join(", ", documentTypes.get(cluster)));

                    if ( ! indexedOnly || indexedDocumentTypes.get(cluster).contains(type)) {
                        reindexing = reindexing.withReady(cluster, type, now);
                        reindexed.computeIfAbsent(cluster, __ -> new TreeSet<>()).add(type);
                    }
                }
            }
            return reindexing;
        });

        return new MessageResponse(reindexed.entrySet().stream()
                                              .filter(cluster -> ! cluster.getValue().isEmpty())
                                              .map(cluster -> "[" + String.join(", ", cluster.getValue()) + "] in '" + cluster.getKey() + "'")
                                              .reduce(new StringJoiner(", ", "Reindexing document types ", " of application " + applicationId)
                                                              .setEmptyValue("Not reindexing any document types of application " + applicationId),
                                                      StringJoiner::add,
                                                      StringJoiner::merge)
                                              .toString());
    }

    public HttpResponse disableReindexing(ApplicationId applicationId) {
        applicationRepository.modifyReindexing(applicationId, reindexing -> reindexing.enabled(false));
        return new MessageResponse("Reindexing disabled");
    }

    private HttpResponse enableReindexing(ApplicationId applicationId) {
        applicationRepository.modifyReindexing(applicationId, reindexing -> reindexing.enabled(true));
        return new MessageResponse("Reindexing enabled");
    }

    private HttpResponse getReindexingStatus(ApplicationId applicationId) {
        Tenant tenant = applicationRepository.getTenant(applicationId);
        if (tenant == null)
            throw new NotFoundException("Tenant '" + applicationId.tenant().value() + "' not found");

        return new ReindexingResponse(getActiveModelOrThrow(applicationId).documentTypesByCluster(),
                                      applicationRepository.getReindexing(applicationId),
                                      applicationRepository.getClusterReindexingStatus(applicationId));
    }

    private HttpResponse restart(ApplicationId applicationId, HttpRequest request) {
        HostFilter filter = HostFilter.from(request.getProperty("hostname"),
                request.getProperty("flavor"),
                request.getProperty("clusterType"),
                request.getProperty("clusterId"));
        applicationRepository.restart(applicationId, filter);
        return new MessageResponse("Success");
    }

    private HttpResponse testerStartTests(ApplicationId applicationId, String suite, HttpRequest request) {
        byte[] data;
        try {
            data = IOUtils.readBytes(request.getData(), 1024 * 1000);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read data in request " + request);
        }
        return applicationRepository.startTests(applicationId, suite, data);
    }

    private HttpResponse validateSecretStore(ApplicationId applicationId, HttpRequest request) {
        var slime = uncheck(() -> SlimeUtils.jsonToSlime(request.getData().readAllBytes()));
        return applicationRepository.validateSecretStore(applicationId, zone.system(), slime);
    }

    private static ApplicationId applicationId(Path path) {
        return ApplicationId.from(path.get("tenant"), path.get("application"), path.get("instance"));
    }

    private static Duration getTimeoutFromRequest(HttpRequest request) {
        return HttpHandler.getRequestTimeout(request, Duration.ofSeconds(5));
    }

    private static Optional<Version> getVespaVersionFromRequest(HttpRequest request) {
        return Optional.ofNullable(request.getProperty("vespaVersion"))
                .filter(s -> !s.isEmpty())
                .map(Version::fromString);
    }

}
