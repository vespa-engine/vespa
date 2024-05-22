// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Query;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.EndpointsChecker.Availability;
import com.yahoo.config.provision.EndpointsChecker.Endpoint;
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
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.ApplicationRepository.TesterSuspendedException;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.JSONResponse;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.http.ReindexingStatusException;
import com.yahoo.vespa.config.server.http.v2.request.ApplicationContentRequest;
import com.yahoo.vespa.config.server.http.v2.response.ApplicationSuspendedResponse;
import com.yahoo.vespa.config.server.http.v2.response.DeleteApplicationResponse;
import com.yahoo.vespa.config.server.http.v2.response.GetApplicationResponse;
import com.yahoo.vespa.config.server.http.v2.response.QuotaUsageResponse;
import com.yahoo.vespa.config.server.http.v2.response.ReindexingResponse;
import com.yahoo.vespa.config.server.tenant.Tenant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;
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
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/active-token-fingerprints")) return activeTokenFingerprints(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/logs")) return logs(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/metrics/deployment")) return deploymentMetrics(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/metrics/searchnode")) return searchNodeMetrics(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/quota")) return quotaUsage(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindexing")) return getReindexingStatus(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/service/{service}/{hostname}/status/{*}")) return serviceStatusPage(applicationId(path), path.get("service"), path.get("hostname"), path.getRest(), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/service/{service}/{hostname}/state/v1/{*}")) return serviceStateV1(applicationId(path), path.get("service"), path.get("hostname"), path.getRest(), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/serviceconverge")) return listServiceConverge(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/serviceconverge/{hostAndPort}")) return checkServiceConverge(applicationId(path), path.get("hostAndPort"), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/suspended")) return isSuspended(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/tester/{command}")) return testerRequest(applicationId(path), path.get("command"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    @Override
    public HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());

        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/verify-endpoints")) return verifyEndpoints(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindex")) return triggerReindexing(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindexing")) return enableReindexing(applicationId(path));
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/restart")) return restart(applicationId(path), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/tester/run/{suite}")) return testerStartTests(applicationId(path), path.get("suite"), request);
        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/validate-secret-store")) return validateSecretStore(applicationId(path), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    @Override
    public HttpResponse handlePUT(HttpRequest request) {
        Path path = new Path(request.getUri());

        if (path.matches("/application/v2/tenant/{tenant}/application/{application}/environment/{ignore}/region/{ignore}/instance/{instance}/reindex")) return updateReindexing(applicationId(path), request);
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
        ServiceListResponse response =
                applicationRepository.servicesToCheckForConfigConvergence(applicationId,
                                                                          getTimeoutFromRequest(request),
                                                                          getVespaVersionFromRequest(request));
        return new HttpServiceListResponse(response, request.getUri());
    }

    private HttpResponse checkServiceConverge(ApplicationId applicationId, String hostAndPort, HttpRequest request) {
        ServiceResponse response =
                applicationRepository.checkServiceForConfigConvergence(applicationId,
                                                                       hostAndPort,
                                                                       getTimeoutFromRequest(request),
                                                                       getVespaVersionFromRequest(request));
        return HttpServiceResponse.createResponse(response, hostAndPort, request.getUri());
    }

    private HttpResponse serviceStatusPage(ApplicationId applicationId, String service, String hostname, HttpURL.Path pathSuffix, HttpRequest request) {
        HttpURL.Path pathPrefix = switch (service) {
            case "container-clustercontroller" -> HttpURL.Path.empty().append("clustercontroller-status").append("v1");
            case "distributor", "storagenode" -> HttpURL.Path.empty().append("contentnode-status").append("v1");
            default -> throw new NotFoundException("No status page for service: " + service);
        };
        return applicationRepository.proxyServiceHostnameRequest(applicationId, hostname, service, pathPrefix.append(pathSuffix), Query.empty().add(request.getJDiscRequest().parameters()), null);
    }

    private HttpResponse serviceStateV1(ApplicationId applicationId, String service, String hostname, HttpURL.Path rest, HttpRequest request) {
        Query query = Query.empty().add(request.getJDiscRequest().parameters());
        String forwardedUrl = query.lastEntries().get("forwarded-url");
        return applicationRepository.proxyServiceHostnameRequest(applicationId, hostname, service,
                                                                 HttpURL.Path.parse("/state/v1").append(rest),
                                                                 query.remove("forwarded-url"),
                                                                 forwardedUrl == null ? null : HttpURL.from(URI.create(forwardedUrl)));
    }

    private HttpResponse content(ApplicationId applicationId, HttpURL.Path contentPath, HttpRequest request) {
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
        return applicationRepository.fileDistributionStatus(applicationId, getTimeoutFromRequest(request));
    }

    private HttpResponse activeTokenFingerprints(ApplicationId applicationId) {
        Slime slime = new Slime();
        Cursor hostsArray = slime.setObject().setArray("hosts");
        applicationRepository.activeTokenFingerprints(applicationId).forEach((host, tokens) -> {
            Cursor hostObject = hostsArray.addObject();
            hostObject.setString("host", host);
            Cursor tokensArray = hostObject.setArray("tokens");
            tokens.forEach(token -> {
                Cursor tokenObject = tokensArray.addObject();
                tokenObject.setString("id", token.id());
                token.fingerprints().forEach(tokenObject.setArray("fingerprints")::addString);
            });
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse logs(ApplicationId applicationId, HttpRequest request) {
        HttpURL requestURL = HttpURL.from(request.getUri());
        Optional<DomainName> hostname = Optional.ofNullable(requestURL.query().lastEntries().get("hostname")).map(DomainName::of);
        return applicationRepository.getLogs(applicationId, hostname, requestURL.query().remove("hostname"));
    }

    private HttpResponse searchNodeMetrics(ApplicationId applicationId) {
        return applicationRepository.getSearchNodeMetrics(applicationId);
    }

    private HttpResponse deploymentMetrics(ApplicationId applicationId) {
        return applicationRepository.getDeploymentMetrics(applicationId);
    }

    private HttpResponse isSuspended(ApplicationId applicationId) {
        return new ApplicationSuspendedResponse(applicationRepository.isSuspended(applicationId));
    }

    private HttpResponse testerRequest(ApplicationId applicationId, String command, HttpRequest request) {
        try {
            return switch (command) {
                case "status" -> applicationRepository.getTesterStatus(applicationId);
                case "log" ->    applicationRepository.getTesterLog(applicationId, Long.valueOf(request.getProperty("after")));
                case "ready" ->  applicationRepository.isTesterReady(applicationId);
                case "report" -> applicationRepository.getTestReport(applicationId);
                default -> throw new IllegalArgumentException("Unknown tester command in request " + request.getUri().toString());
            };
        }
        catch (TesterSuspendedException e) {
            return HttpErrorResponse.testerSuspended(e.getMessage());
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
        double speed = Double.parseDouble(Objects.requireNonNullElse(request.getProperty("speed"), "1"));
        String cause = Objects.requireNonNullElse(request.getProperty("cause"), "reindexing for an unknown reason");
        Instant now = applicationRepository.clock().instant();

        return modifyReindexing(applicationId, request,
                                (original, cluster, type) -> original.withReady(cluster, type, now, speed, cause),
                                new StringJoiner(", ", "Reindexing document types ", " of application " + applicationId)
                                        .setEmptyValue("Not reindexing any document types of application " + applicationId));
    }

    private HttpResponse updateReindexing(ApplicationId applicationId, HttpRequest request) {
        String speedValue = request.getProperty("speed");
        if (speedValue == null)
            throw new IllegalArgumentException("request must specify 'speed' parameter");

        return modifyReindexing(applicationId, request,
                                (original, cluster, type) -> original.withSpeed(cluster, type, Double.parseDouble(speedValue)),
                                new StringJoiner(", ", "Set reindexing speed to '" + speedValue + "' for document types ", " of application " + applicationId)
                                        .setEmptyValue("Changed reindexing of no document types of application " + applicationId));
    }

    private interface ReindexingModification {
        ApplicationReindexing apply(ApplicationReindexing original, String cluster, String type);
    }

    private HttpResponse modifyReindexing(ApplicationId applicationId, HttpRequest request,
                                          ReindexingModification modification, StringJoiner messageBuilder) {
        Model model = getActiveModelOrThrow(applicationId);
        Map<String, Set<String>> documentTypes = model.documentTypesByCluster();
        Map<String, Set<String>> indexedDocumentTypes = model.indexedDocumentTypesByCluster();

        boolean indexedOnly = request.getBooleanProperty("indexedOnly");
        Set<String> clusters = StringUtilities.split(request.getProperty("clusterId"));
        Set<String> types = StringUtilities.split(request.getProperty("documentType"));

        Map<String, Set<String>> reindexed = new TreeMap<>();
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
                        reindexing = modification.apply(reindexing, cluster, type);
                        reindexed.computeIfAbsent(cluster, __ -> new TreeSet<>()).add(type);
                    }
                }
            }
            return reindexing;
        });

        return new MessageResponse(reindexed.entrySet().stream()
                                            .filter(cluster -> ! cluster.getValue().isEmpty())
                                            .map(cluster -> "[" + String.join(", ", cluster.getValue()) + "] in '" + cluster.getKey() + "'")
                                            .reduce(messageBuilder, StringJoiner::add, StringJoiner::merge)
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

        try {
            Map<String, Set<String>> documentTypes = getActiveModelOrThrow(applicationId).documentTypesByCluster();
            ApplicationReindexing reindexing = applicationRepository.getReindexing(applicationId);
            Map<String, ClusterReindexing> clusters = applicationRepository.getClusterReindexingStatus(applicationId);
            return new ReindexingResponse(documentTypes, reindexing, clusters);
        } catch (UncheckedIOException e) {
            throw new ReindexingStatusException("Reindexing status for '" + applicationId +
                                                "' is currently unavailable");
        }
    }

    private HttpResponse restart(ApplicationId applicationId, HttpRequest request) {
        HostFilter filter = HostFilter.from(request.getProperty("hostname"),
                request.getProperty("flavor"),
                request.getProperty("clusterType"),
                request.getProperty("clusterId"));
        applicationRepository.restart(applicationId, filter);
        return new MessageResponse("Success");
    }

    private HttpResponse verifyEndpoints(ApplicationId applicationId, HttpRequest request) {
        byte[] data = uncheck(() -> request.getData().readAllBytes());
        List<Endpoint> endpoints = new ArrayList<>();
        SlimeUtils.jsonToSlime(data).get()
                  .field("endpoints")
                  .traverse((ArrayTraverser) (__, endpointObject) -> {
                      endpoints.add(new Endpoint(applicationId,
                                                 ClusterSpec.Id.from(endpointObject.field("clusterName").asString()),
                                                 HttpURL.from(URI.create(endpointObject.field("url").asString())),
                                                 SlimeUtils.optionalString(endpointObject.field("ipAddress")).map(uncheck(InetAddress::getByName)),
                                                 SlimeUtils.optionalString(endpointObject.field("canonicalName")).map(DomainName::of),
                                                 endpointObject.field("public").asBool(),
                                                 CloudAccount.from(endpointObject.field("account").asString())));
                  });
        if (endpoints.isEmpty()) throw new IllegalArgumentException("No endpoints in request " + request);

        Availability availability = applicationRepository.verifyEndpoints(endpoints);
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("status", switch (availability.status()) {
            case available -> "available";
            case endpointsUnavailable -> "endpointsUnavailable";
            case containersUnhealthy -> "containersUnhealthy";
        });
        root.setString("message", availability.message());
        return new SlimeJsonResponse(slime);
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

    static class HttpServiceResponse extends JSONResponse {

        public static HttpServiceResponse createResponse(ConfigConvergenceChecker.ServiceResponse serviceResponse, String hostAndPort, URI uri) {
            return switch (serviceResponse.status) {
                case ok ->
                        createOkResponse(uri, hostAndPort, serviceResponse.wantedGeneration, serviceResponse.currentGeneration, serviceResponse.converged);
                case hostNotFound ->
                        createHostNotFoundInAppResponse(uri, hostAndPort, serviceResponse.wantedGeneration);
                case notFound ->
                        createNotFoundResponse(uri, hostAndPort, serviceResponse.wantedGeneration, serviceResponse.errorMessage.orElse(""));
                case error ->
                        createErrorResponse(uri, hostAndPort, serviceResponse.wantedGeneration, serviceResponse.errorMessage.orElse(""));
            };
        }

        private HttpServiceResponse(int status, URI uri, String hostname, Long wantedGeneration) {
            super(status);
            object.setString("url", uri.toString());
            object.setString("host", hostname);
            object.setLong("wantedGeneration", wantedGeneration);
        }

        private static HttpServiceResponse createOkResponse(URI uri, String hostname, Long wantedGeneration, Long currentGeneration, boolean converged) {
            HttpServiceResponse serviceResponse = new HttpServiceResponse(200, uri, hostname, wantedGeneration);
            serviceResponse.object.setBool("converged", converged);
            serviceResponse.object.setLong("currentGeneration", currentGeneration);
            return serviceResponse;
        }

        private static HttpServiceResponse createHostNotFoundInAppResponse(URI uri, String hostname, Long wantedGeneration) {
            HttpServiceResponse serviceResponse = new HttpServiceResponse(410, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("problem", "Host:port (service) no longer part of application, refetch list of services.");
            return serviceResponse;
        }

        private static HttpServiceResponse createErrorResponse(URI uri, String hostname, Long wantedGeneration, String error) {
            HttpServiceResponse serviceResponse = new HttpServiceResponse(500, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("error", error);
            return serviceResponse;
        }

        private static HttpServiceResponse createNotFoundResponse(URI uri, String hostname, Long wantedGeneration, String error) {
            HttpServiceResponse serviceResponse = new HttpServiceResponse(404, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("error", error);
            return serviceResponse;
        }

    }

    static class HttpServiceListResponse extends JSONResponse {

        // Pre-condition: servicesToCheck has a state port
        public HttpServiceListResponse(ConfigConvergenceChecker.ServiceListResponse response, URI uri) {
            super(200);
            Cursor serviceArray = object.setArray("services");
            response.services().forEach((service) -> {
                ServiceInfo serviceInfo = service.serviceInfo;
                Cursor serviceObject = serviceArray.addObject();
                String hostName = serviceInfo.getHostName();
                int statePort = ConfigConvergenceChecker.getStatePort(serviceInfo).get();
                serviceInfo.getProperty("clustername").ifPresent(clusterName -> serviceObject.setString("clusterName", clusterName));
                serviceObject.setString("host", hostName);
                serviceObject.setLong("port", statePort);
                serviceObject.setString("type", serviceInfo.getServiceType());
                serviceObject.setString("url", uri.toString() + "/" + hostName + ":" + statePort);
                serviceObject.setLong("currentGeneration", service.currentGeneration);
            });
            object.setString("url", uri.toString());
            object.setLong("currentGeneration", response.currentGeneration);
            object.setLong("wantedGeneration", response.wantedGeneration);
            object.setBool("converged", response.converged);
        }
    }

}
