// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.json.Jackson;
import ai.vespa.util.http.hc5.VespaAsyncHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.model.api.ApplicationClusterInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import com.yahoo.text.Text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static java.util.logging.Level.FINE;

/**
 * Checks for convergence of config generation for a given application.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 * @author bjorncs
 */
public class ConfigConvergenceChecker extends AbstractComponent {

    private static final Logger log = Logger.getLogger(ConfigConvergenceChecker.class.getName());

    private static final Set<String> serviceTypesToCheck = Set.of(
            CONTAINER.serviceName,
            LOGSERVER_CONTAINER.serviceName,
            CLUSTERCONTROLLER_CONTAINER.serviceName,
            METRICS_PROXY_CONTAINER.serviceName,
            "searchnode",
            "storagenode",
            "distributor"
    );


    private final ExecutorService responseHandlerExecutor =
            Executors.newSingleThreadExecutor(new DaemonThreadFactory("config-convergence-checker-response-handler-"));
    private BooleanFlag useStateV1ExtendedInfo;

    @Inject
    public ConfigConvergenceChecker(FlagSource flagSource) {
        this.useStateV1ExtendedInfo = Flags.USE_WANTED_GENERATION_IN_CONVERGENCE_CHECK.bindTo(flagSource);
    }

    /** Fetches the active config generation for all services in the given application. */
    public Map<ServiceInfo, Long> getServiceConfigGenerations(Application application, Duration timeoutPerService) {
        return getServiceConfigGenerations(application, timeoutPerService, new HostsToCheck(Set.of()));
    }

    /**
     * Fetches the active config generation for all services in the given application. Will not check services
     * which defer config changes until restart if checkAll is false. hostsToCheck are names to check, or empty to check all.
     */
    private Map<ServiceInfo, Long> getServiceConfigGenerations(Application application,
                                                               Duration timeoutPerService,
                                                               HostsToCheck hostsToCheck) {
        List<ServiceInfo> servicesToCheck = collectServicesToCheck(application, hostsToCheck);
        log.log(FINE, () -> "Services to check for config convergence: " + servicesToCheck);
        useStateV1ExtendedInfo = useStateV1ExtendedInfo.with(Dimension.INSTANCE_ID, application.getId().serializedForm());
        return getServiceGenerations(servicesToCheck, timeoutPerService).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().generation(),
                                          (a, b) -> a, LinkedHashMap::new));
    }

    /** Checks all services in given application. Returns the minimum current generation of all services */
    public ServiceListResponse checkConvergenceForAllServices(Application application, Duration timeoutPerService) {
        return checkConvergence(application, timeoutPerService, new HostsToCheck(Set.of()));
    }

    /**
     * Checks services except those which defer config changes until restart in the given application.
     * Returns the minimum current generation of those services.
     */
    public ServiceListResponse checkConvergenceUnlessDeferringChangesUntilRestart(Application application, Set<String> hostnames) {
        Duration timeoutPerService = Duration.ofSeconds(10);
        return checkConvergence(application, timeoutPerService, new HostsToCheck(hostnames));
    }

    private ServiceListResponse checkConvergence(Application application, Duration timeoutPerService, HostsToCheck hostsToCheck) {
        Map<ServiceInfo, ServiceGenerationResult> results = getServiceGenerations(collectServicesToCheck(application, hostsToCheck), timeoutPerService);
        long wantedGeneration = application.getApplicationGeneration();
        long currentGeneration = results.values().stream().mapToLong(ServiceGenerationResult::generation).min().orElse(-1);
        List<ServiceListResponse.Service> services = results.entrySet().stream()
                .map(e -> new ServiceListResponse.Service(e.getKey(), e.getValue().generation(), e.getValue().configStatus()))
                .toList();
        return new ServiceListResponse(services, wantedGeneration, currentGeneration, currentGeneration >= wantedGeneration);
    }

    private List<ServiceInfo> collectServicesToCheck(Application application, HostsToCheck hostsToCheck) {
        List<ServiceInfo> servicesToCheck = new ArrayList<>();
        application.getModel().getHosts()
                   .forEach(host -> host.getServices().stream()
                                        .filter(service -> serviceTypesToCheck.contains(service.getServiceType()))
                                        .filter(service -> shouldCheckService(hostsToCheck, application, service))
                                        .forEach(service -> getStatePort(service).ifPresent(port -> servicesToCheck.add(service))));
        return servicesToCheck;
    }

    /** Check service identified by host and port in given application */
    public ServiceResponse getServiceConfigGeneration(Application application, String hostAndPortToCheck, Duration timeout) {
        Long wantedGeneration = application.getApplicationGeneration();
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            if ( ! hostInApplication(application, hostAndPortToCheck))
                return new ServiceResponse(ServiceResponse.Status.hostNotFound, wantedGeneration);
            ServiceGenerationResult result = getServiceGeneration(client, URI.create("http://" + hostAndPortToCheck), timeout).get();
            if (result.configStatus().isFailed() && result.configStatus.generation() >= wantedGeneration)
                return new ServiceResponse(ServiceResponse.Status.error, wantedGeneration, result.configStatus().message());
            boolean converged = result.generation() >= wantedGeneration;
            return new ServiceResponse(ServiceResponse.Status.ok, wantedGeneration, result.generation(), converged);
        } catch (InterruptedException | ExecutionException | CancellationException e) { // e.g. if we cannot connect to the service to find generation
            return new ServiceResponse(ServiceResponse.Status.notFound, wantedGeneration, e.getMessage());
        } catch (Exception e) {
            return new ServiceResponse(ServiceResponse.Status.error, wantedGeneration, e.getMessage());
        }
    }

    private boolean shouldCheckService(HostsToCheck hostsToCheck, Application application, ServiceInfo serviceInfo) {
        if (hostsToCheck.checkAll()) return true;
        if ( ! hostsToCheck.check(serviceInfo.getHostName())) return false;
        if (isNotContainer(serviceInfo)) return true;
        return serviceIsInClusterWhichShouldBeChecked(application, serviceInfo);
    }

    private boolean isNotContainer(ServiceInfo serviceInfo) {
        return ! List.of(CONTAINER.serviceName, METRICS_PROXY_CONTAINER).contains(serviceInfo.getServiceType());
    }

    // Don't check service in a cluster which uses restartOnDeploy (new config will not be used until service is restarted)
    private boolean serviceIsInClusterWhichShouldBeChecked(Application application, ServiceInfo serviceInfo) {
        Set<ApplicationClusterInfo> excludeFromChecking = application.getModel().applicationClusterInfo()
                                                                     .stream()
                                                                     .filter(ApplicationClusterInfo::getDeferChangesUntilRestart)
                                                                     .collect(Collectors.toSet());
        log.log(FINE, "Exclude services from these clusters when checking config convergence: " +
                excludeFromChecking.stream().map(ApplicationClusterInfo::name).collect(Collectors.joining(", ")));

        return excludeFromChecking.stream().noneMatch(info -> info.name().equals(serviceInfo.getProperty("clustername").orElse("")));
    }

    /** Gets service generation for a list of services (in parallel). */
    private Map<ServiceInfo, ServiceGenerationResult> getServiceGenerations(List<ServiceInfo> services, Duration timeout) {
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            List<CompletableFuture<Void>> inprogressRequests = new ArrayList<>();
            ConcurrentMap<ServiceInfo, ServiceGenerationResult> temporaryResult = new ConcurrentHashMap<>();
            for (ServiceInfo service : services) {
                int statePort = getStatePort(service).orElse(0);
                if (statePort <= 0) continue;
                URI uri = URI.create("http://" + service.getHostName() + ":" + statePort);
                CompletableFuture<Void> inprogressRequest = getServiceGeneration(client, uri, timeout)
                        .handle((result, error) -> {
                            if (result != null) {
                                temporaryResult.put(service, result);
                            } else {
                                log.log(
                                        FINE,
                                        error,
                                        () -> Text.format("Failed to retrieve service config generation for '%s': %s", service, error.getMessage()));
                                temporaryResult.put(service, ServiceGenerationResult.unreachable(error.getMessage()));
                            }
                            return null;
                        });
                inprogressRequests.add(inprogressRequest);
            }
            CompletableFuture.allOf(inprogressRequests.toArray(CompletableFuture[]::new)).join();
            return createMapOrderedByServiceList(services, temporaryResult);
        } catch (IOException e) {
            // Actual client implementation does not throw IOException on close()
            throw new UncheckedIOException(e);
        }
    }

    /** Get service generation of service at given URL */
    private CompletableFuture<ServiceGenerationResult> getServiceGeneration(CloseableHttpAsyncClient client, URI serviceUrl, Duration timeout) {
        SimpleHttpRequest request = SimpleRequestBuilder.get(createApiUri(serviceUrl)).build();
        request.setConfig(createRequestConfig(timeout));

        // Ignoring returned Future object as we want to use the more flexible CompletableFuture instead
        CompletableFuture<SimpleHttpResponse> responsePromise = new CompletableFuture<>();
        client.execute(request, new FutureCallback<>() {
            @Override public void completed(SimpleHttpResponse result) { responsePromise.complete(result); }
            @Override public void failed(Exception ex) { responsePromise.completeExceptionally(ex); }
            @Override public void cancelled() { responsePromise.cancel(false); }
        });

        // Don't do json parsing in http client's thread
        return responsePromise.thenApplyAsync(this::handleResponse, responseHandlerExecutor);
    }

    private ServiceGenerationResult handleResponse(SimpleHttpResponse response) throws UncheckedIOException {
        try {
            int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK) throw new IOException("Expected status code 200, got " + statusCode);
            if (response.getBody() == null) throw new IOException("Response has no content");
            JsonNode json = Jackson.mapper().readTree(response.getBodyText());
            JsonNode configNode = json.get("config");
            long generation = configNode.get("generation").asLong(-1);
            long wantedGeneration = configNode.path("wantedGeneration").asLong(generation);
            if (useStateV1ExtendedInfo.value() && configNode.get("message") != null) {
                return ServiceGenerationResult.configFailed(wantedGeneration,
                                                            configNode.path("message").asText("unknown failure"));
            }
            return ServiceGenerationResult.ok(generation);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean hostInApplication(Application application, String hostPort) {
        for (HostInfo host : application.getModel().getHosts()) {
            if (hostPort.startsWith(host.getHostname())) {
                for (ServiceInfo service : host.getServices()) {
                    for (PortInfo port : service.getPorts()) {
                        if (hostPort.equals(host.getHostname() + ":" + port.getPort())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static Optional<Integer> getStatePort(ServiceInfo service) {
        return service.getPorts().stream()
                      .filter(port -> port.getTags().contains("state"))
                      .map(PortInfo::getPort)
                      .findFirst();
    }

    @Override
    public void deconstruct()  {
        responseHandlerExecutor.shutdown();
        try {
            responseHandlerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Unable to shutdown executor", e);
        }
    }

    private static Map<ServiceInfo, ServiceGenerationResult> createMapOrderedByServiceList(
            List<ServiceInfo> services, ConcurrentMap<ServiceInfo, ServiceGenerationResult> result) {
        Map<ServiceInfo, ServiceGenerationResult> orderedResult = new LinkedHashMap<>();
        for (ServiceInfo service : services) {
            ServiceGenerationResult generation = result.get(service);
            if (generation != null) {
                orderedResult.put(service, generation);
            }
        }
        return orderedResult;
    }

    private record ServiceGenerationResult(long generation, ConfigStatus configStatus) {
        static ServiceGenerationResult ok(long generation) { return new ServiceGenerationResult(generation, ConfigStatus.ok(generation)); }
        static ServiceGenerationResult configFailed(long generation, String message) { return new ServiceGenerationResult(-1L, ConfigStatus.failed(generation, message)); }
        static ServiceGenerationResult unreachable(String message) { return new ServiceGenerationResult(-1L, ConfigStatus.unknown(-1, message)); }
    }

    private static URI createApiUri(URI serviceUrl) {
        try {
            return new URIBuilder(serviceUrl)
                    .setPath("/state/v1/config")
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static RequestConfig createRequestConfig(Duration timeout) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .build();
    }

    private static CloseableHttpAsyncClient createHttpClient() {
        return VespaAsyncHttpClientBuilder
                .create(tlsStrategy ->
                        PoolingAsyncClientConnectionManagerBuilder.create()
                                .setMaxConnTotal(100)
                                .setMaxConnPerRoute(10)
                                .setDefaultConnectionConfig(ConnectionConfig.custom()
                                        .setTimeToLive(TimeValue.ofMilliseconds(1))
                                        .setConnectTimeout(Timeout.ofSeconds(10)) // Times out at 1s over wireguard with 500+ services.
                                        .build())
                                .setTlsStrategy(tlsStrategy)
                                .build())
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(2))
                        .build())
                .setUserAgent("config-convergence-checker")
                .build();
    }

    private record HostsToCheck(Set<String> hostnames) {

        public boolean checkAll() { return hostnames.isEmpty(); }

        public boolean check(String hostname) { return checkAll() || hostnames.contains(hostname); }

    }

    public record ConfigStatus(long generation, Status status, String message) {
        public enum Status {
            OK, FAILED, UNKNOWN;
            @Override public String toString() { return name().toLowerCase(); }
        }
        public static ConfigStatus ok(long generation) {
            return new ConfigStatus(generation, Status.OK, null);
        }
        public static ConfigStatus failed(long generation, String message) {
            return new ConfigStatus(generation, Status.FAILED, message);
        }
        public static ConfigStatus unknown(long generation, String message) {
            return new ConfigStatus(generation, Status.UNKNOWN, message);
        }
        public boolean isFailed() { return status == Status.FAILED; }
    }

    public static class ServiceResponse {

        public enum Status { ok, notFound, hostNotFound, error }

        public final Status status;
        public final Long wantedGeneration;
        public final Long currentGeneration;
        public final boolean converged;
        public final Optional<String> errorMessage;

        public ServiceResponse(Status status, long wantedGeneration) {
            this(status, wantedGeneration, 0);
        }

        public ServiceResponse(Status status, long wantedGeneration, long currentGeneration) {
            this(status, wantedGeneration, currentGeneration, false);
        }

        public ServiceResponse(Status status, long wantedGeneration, long currentGeneration, boolean converged) {
            this(status, wantedGeneration, currentGeneration, converged, Optional.empty());
        }

        public ServiceResponse(Status status, long wantedGeneration, String errorMessage) {
            this(status, wantedGeneration, 0, false, Optional.ofNullable(errorMessage));
        }

        private ServiceResponse(Status status, long wantedGeneration, long currentGeneration, boolean converged, Optional<String> errorMessage) {
            this.status = status;
            this.wantedGeneration = wantedGeneration;
            this.currentGeneration = currentGeneration;
            this.converged = converged;
            this.errorMessage = errorMessage;
        }

    }

    public static class ServiceListResponse {

        public final List<Service> services = new ArrayList<>();
        public final long wantedGeneration;
        public final long currentGeneration;
        public final boolean converged;

        public ServiceListResponse(List<Service> services, long wantedGeneration, long currentGeneration, boolean converged) {
            this.services.addAll(services);
            this.wantedGeneration = wantedGeneration;
            this.currentGeneration = currentGeneration;
            this.converged = converged;
        }

        public ServiceListResponse(Map<ServiceInfo, Long> services, long wantedGeneration, long currentGeneration) {
            this(services.entrySet().stream()
                         .map(e -> new Service(e.getKey(), e.getValue(), ConfigStatus.ok(currentGeneration)))
                         .toList(),
                 wantedGeneration, currentGeneration, currentGeneration >= wantedGeneration);
        }

        public ServiceListResponse unconverged() {
            return new ServiceListResponse(services, wantedGeneration, currentGeneration, false);
        }

        public List<Service> services() { return services; }

        public static class Service {

            public final ServiceInfo serviceInfo;
            public final long currentGeneration;
            public final ConfigStatus configStatus;

            public Service(ServiceInfo serviceInfo, long currentGeneration, ConfigStatus configStatus) {
                this.serviceInfo = serviceInfo;
                this.currentGeneration = currentGeneration;
                this.configStatus = configStatus;
            }

        }

    }

}
