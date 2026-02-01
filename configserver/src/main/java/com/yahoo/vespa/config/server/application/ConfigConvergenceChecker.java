// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.ServiceConfigState;
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
 * @author glebashnik
 */
public class ConfigConvergenceChecker extends AbstractComponent {

    private static final Logger log = Logger.getLogger(ConfigConvergenceChecker.class.getName());

    private final static Set<String> serviceTypesToCheck = Set.of(
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

    @Inject
    public ConfigConvergenceChecker() {}

    /**
     * Fetches config state for all services in the given application.
     */
    public Map<ServiceInfo, ServiceConfigState> getServiceConfigStatesUnlessDeferringChangesUntilRestart(
            Application application, Duration timeoutPerService) {
        return getServiceConfigStatesUnlessDeferringChangesUntilRestart(
                application, timeoutPerService, new HostsToCheck(Set.of()));
    }

    /**
     * Fetches config state for all services in the given application with {@code hostsToCheck} hostnames or all if empty.
     * Will not check services which defer config changes until restart if checkAll is false.
     */
    private Map<ServiceInfo, ServiceConfigState> getServiceConfigStatesUnlessDeferringChangesUntilRestart(
            Application application, Duration timeoutPerService, HostsToCheck hostsToCheck) {
        List<ServiceInfo> servicesToCheck = new ArrayList<>();
        application.getModel().getHosts()
                   .forEach(host -> host.getServices().stream()
                                        .filter(service -> serviceTypesToCheck.contains(service.getServiceType()))
                                        .filter(service -> shouldCheckService(hostsToCheck, application, service))
                                        .forEach(service -> getStatePort(service).ifPresent(port -> servicesToCheck.add(service))));

        log.log(FINE, () -> "Services to check for config state: " + servicesToCheck);
        return getServiceConfigStates(servicesToCheck, timeoutPerService);
    }

    /**
     * Fetches config state for all services in the given application with hostnames specified in {@code hostsToCheck} or all if empty.
     */
    public Map<ServiceInfo, ServiceConfigState> getServiceConfigStates(
            Application application, Duration timeoutPerService, Set<String> hostnames) {
        HostsToCheck hostsToCheck = new HostsToCheck(hostnames);
        
        List<ServiceInfo> servicesToCheck = application.getModel().getHosts().stream()
                .flatMap(host -> host.getServices().stream()
                        .filter(service -> hostsToCheck.check(service.getHostName())
                                && serviceTypesToCheck.contains(service.getServiceType())
                                && getStatePort(service).isPresent()))
                .toList();
        
        log.log(FINE, () -> "Services to check for config state: " + servicesToCheck);
        return getServiceConfigStates(servicesToCheck, timeoutPerService);
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
        Map<ServiceInfo, ServiceConfigState> currentGenerations = getServiceConfigStatesUnlessDeferringChangesUntilRestart(application, timeoutPerService, hostsToCheck);
        long currentGeneration = currentGenerations.values().stream().map(ServiceConfigState::currentGeneration).mapToLong(Long::longValue).min().orElse(-1);
        return new ServiceListResponse(currentGenerations, application.getApplicationGeneration(), currentGeneration);
    }

    /** Check service identified by host and port in given application */
    public ServiceResponse getServiceConfigGeneration(Application application, String hostAndPortToCheck, Duration timeout) {
        Long wantedGeneration = application.getApplicationGeneration();
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            if ( ! hostInApplication(application, hostAndPortToCheck))
                return new ServiceResponse(ServiceResponse.Status.hostNotFound, wantedGeneration);
            ServiceConfigState serviceConfigState = getServiceConfigState(
                            client, URI.create("http://" + hostAndPortToCheck), timeout)
                    .get();
            long currentGeneration = serviceConfigState.currentGeneration();
            boolean converged = currentGeneration >= wantedGeneration;
            return new ServiceResponse(ServiceResponse.Status.ok, wantedGeneration, currentGeneration, converged);
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

        return excludeFromChecking.stream().noneMatch(info -> info.name()
                .equals(serviceInfo.getProperty("clustername").orElse("")));
    }

    /** Gets service generation for a list of services (in parallel). */
    private Map<ServiceInfo, ServiceConfigState> getServiceConfigStates(List<ServiceInfo> services, Duration timeout) {
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            List<CompletableFuture<Void>> inprogressRequests = new ArrayList<>();
            ConcurrentMap<ServiceInfo, ServiceConfigState> temporaryResult = new ConcurrentHashMap<>();
            for (ServiceInfo service : services) {
                int statePort = getStatePort(service).orElse(0);
                if (statePort <= 0) continue;
                URI uri = URI.create("http://" + service.getHostName() + ":" + statePort);
                CompletableFuture<Void> inprogressRequest = getServiceConfigState(client, uri, timeout)
                        .handle((result, error) -> {
                            if (result != null) {
                                temporaryResult.put(service, result);
                            } else {
                                log.log(
                                        FINE,
                                        error,
                                        () -> String.format(
                                                "Failed to retrieve service config generation for '%s': %s",
                                                service, error.getMessage()));
                                temporaryResult.put(service, new ServiceConfigState(-1L, Optional.empty()));
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
    private CompletableFuture<ServiceConfigState> getServiceConfigState(CloseableHttpAsyncClient client, URI serviceUrl, Duration timeout) {
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

    private ServiceConfigState handleResponse(SimpleHttpResponse response) throws UncheckedIOException {
        try {
            int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK) throw new IOException("Expected status code 200, got " + statusCode);
            if (response.getBody() == null) throw new IOException("Response has no content");
            return serviceConfigStateFromJson(Jackson.mapper().readTree(response.getBodyText()));
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

    private static ServiceConfigState serviceConfigStateFromJson(JsonNode state) {
        JsonNode configNode = state.get("config");
        long generation = configNode.get("generation").asLong(-1);
        Optional<Boolean> applyOnRestart = configNode.has("applyOnRestart") ?
                Optional.of(configNode.get("applyOnRestart").asBoolean()) : Optional.empty();
        return new ServiceConfigState(generation, applyOnRestart);
    }

    private static Map<ServiceInfo, ServiceConfigState> createMapOrderedByServiceList(
            List<ServiceInfo> services, ConcurrentMap<ServiceInfo, ServiceConfigState> result) {
        Map<ServiceInfo, ServiceConfigState> orderedResult = new LinkedHashMap<>();
        for (ServiceInfo service : services) {
            ServiceConfigState state = result.get(service);
            if (state != null) {
                orderedResult.put(service, state);
            }
        }
        return orderedResult;
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

        private ServiceListResponse(List<Service> services, long wantedGeneration, long currentGeneration, boolean converged) {
            this.services.addAll(services);
            this.wantedGeneration = wantedGeneration;
            this.currentGeneration = currentGeneration;
            this.converged = converged;
        }
        public ServiceListResponse(Map<ServiceInfo, ServiceConfigState> services, long wantedGeneration, long currentGeneration) {
            this(services.entrySet().stream().map(entry -> new Service(entry.getKey(), entry.getValue())).toList(),
                 wantedGeneration,
                 currentGeneration,
                 currentGeneration >= wantedGeneration);
        }

        public ServiceListResponse unconverged() {
            return new ServiceListResponse(services, wantedGeneration, currentGeneration, false);
        }

        public List<Service> services() { return services; }

        public static class Service {
            public final ServiceInfo serviceInfo;
            public final ServiceConfigState serviceConfigState;

            public Service(ServiceInfo serviceInfo, ServiceConfigState serviceConfigState) {
                this.serviceInfo = serviceInfo;
                this.serviceConfigState = serviceConfigState;
            }

        }

    }

}
