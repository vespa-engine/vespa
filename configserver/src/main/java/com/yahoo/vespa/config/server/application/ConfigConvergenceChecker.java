// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.util.http.VespaAsyncHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;

/**
 * Checks for convergence of config generation for a given application.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 * @author bjorncs
 */
public class ConfigConvergenceChecker extends AbstractComponent {

    private static final Logger log = Logger.getLogger(ConfigConvergenceChecker.class.getName());

    private final static Set<String> serviceTypesToCheck = Set.of(
            CONTAINER.serviceName,
            QRSERVER.serviceName,
            LOGSERVER_CONTAINER.serviceName,
            CLUSTERCONTROLLER_CONTAINER.serviceName,
            "searchnode",
            "storagenode",
            "distributor"
    );


    private final Executor responseHandlerExecutor =
            Executors.newSingleThreadExecutor(new DaemonThreadFactory("config-convergence-checker-response-handler-"));
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Inject
    public ConfigConvergenceChecker() {}

    /** Fetches the active config generation for all services in the given application. */
    public Map<ServiceInfo, Long> getServiceConfigGenerations(Application application, Duration timeoutPerService) {
        List<ServiceInfo> servicesToCheck = new ArrayList<>();
        application.getModel().getHosts()
                   .forEach(host -> host.getServices().stream()
                                        .filter(service -> serviceTypesToCheck.contains(service.getServiceType()))
                                        .forEach(service -> getStatePort(service).ifPresent(port -> servicesToCheck.add(service))));

        return getServiceGenerations(servicesToCheck, timeoutPerService);
    }

    /** Check all services in given application. Returns the minimum current generation of all services */
    public JSONResponse getServiceConfigGenerationsResponse(Application application, URI requestUrl, Duration timeoutPerService) {
        Map<ServiceInfo, Long> currentGenerations = getServiceConfigGenerations(application, timeoutPerService);
        long currentGeneration = currentGenerations.values().stream().mapToLong(Long::longValue).min().orElse(-1);
        return new ServiceListResponse(200, currentGenerations, requestUrl, application.getApplicationGeneration(),
                                       currentGeneration);
    }

    /** Check service identified by host and port in given application */
    public JSONResponse getServiceConfigGenerationResponse(Application application, String hostAndPortToCheck, URI requestUrl, Duration timeout) {
        Long wantedGeneration = application.getApplicationGeneration();
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            if ( ! hostInApplication(application, hostAndPortToCheck))
                return ServiceResponse.createHostNotFoundInAppResponse(requestUrl, hostAndPortToCheck, wantedGeneration);
            long currentGeneration = getServiceGeneration(client, URI.create("http://" + hostAndPortToCheck), timeout).get();
            boolean converged = currentGeneration >= wantedGeneration;
            return ServiceResponse.createOkResponse(requestUrl, hostAndPortToCheck, wantedGeneration, currentGeneration, converged);
        } catch (InterruptedException | ExecutionException | CancellationException e) { // e.g. if we cannot connect to the service to find generation
            return ServiceResponse.createNotFoundResponse(requestUrl, hostAndPortToCheck, wantedGeneration, e.getMessage());
        } catch (Exception e) {
            return ServiceResponse.createErrorResponse(requestUrl, hostAndPortToCheck, wantedGeneration, e.getMessage());
        }
    }

    /** Gets service generation for a list of services (in parallel). */
    private Map<ServiceInfo, Long> getServiceGenerations(List<ServiceInfo> services, Duration timeout) {
        try (CloseableHttpAsyncClient client = createHttpClient()) {
            client.start();
            List<CompletableFuture<Void>> inprogressRequests = new ArrayList<>();
            ConcurrentMap<ServiceInfo, Long> temporaryResult = new ConcurrentHashMap<>();
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
                                        Level.FINE,
                                        error,
                                        () -> String.format("Failed to retrieve service config generation for '%s': %s", service, error.getMessage()));
                                temporaryResult.put(service, -1L);
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
    private CompletableFuture<Long> getServiceGeneration(CloseableHttpAsyncClient client, URI serviceUrl, Duration timeout) {
        SimpleHttpRequest request = SimpleHttpRequests.get(createApiUri(serviceUrl));
        request.setHeader("Connection", "close");
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

    private long handleResponse(SimpleHttpResponse response) throws UncheckedIOException {
        try {
            int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK) throw new IOException("Expected status code 200, got " + statusCode);
            if (response.getBody() == null) throw new IOException("Response has no content");
            return generationFromContainerState(jsonMapper.readTree(response.getBodyText()));
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

    private static Optional<Integer> getStatePort(ServiceInfo service) {
        return service.getPorts().stream()
                      .filter(port -> port.getTags().contains("state"))
                      .map(PortInfo::getPort)
                      .findFirst();
    }

    private static long generationFromContainerState(JsonNode state) {
        return state.get("config").get("generation").asLong(-1);
    }

    private static Map<ServiceInfo, Long> createMapOrderedByServiceList(
            List<ServiceInfo> services, ConcurrentMap<ServiceInfo, Long> result) {
        Map<ServiceInfo, Long> orderedResult = new LinkedHashMap<>();
        for (ServiceInfo service : services) {
            Long generation = result.get(service);
            if (generation != null) {
                orderedResult.put(service, generation);
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
                .setConnectionRequestTimeout(Timeout.ofSeconds(1))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setConnectTimeout(Timeout.ofSeconds(1))
                .build();
    }

    private static CloseableHttpAsyncClient createHttpClient() {
        return VespaAsyncHttpClientBuilder
                .create(tlsStrategy ->
                        PoolingAsyncClientConnectionManagerBuilder.create()
                                .setMaxConnTotal(100)
                                .setMaxConnPerRoute(10)
                                .setTlsStrategy(tlsStrategy)
                                .build())
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(2))
                        .build())
                .setUserAgent("config-convergence-checker")
                .setConnectionReuseStrategy((request, response, context) -> false) // Disable connection reuse
                .build();
    }

    private static class ServiceListResponse extends JSONResponse {

        // Pre-condition: servicesToCheck has a state port
        private ServiceListResponse(int status, Map<ServiceInfo, Long> servicesToCheck, URI uri, long wantedGeneration,
                                    long currentGeneration) {
            super(status);
            Cursor serviceArray = object.setArray("services");
            servicesToCheck.forEach((service, generation) -> {
                Cursor serviceObject = serviceArray.addObject();
                String hostName = service.getHostName();
                int statePort = getStatePort(service).get();
                serviceObject.setString("host", hostName);
                serviceObject.setLong("port", statePort);
                serviceObject.setString("type", service.getServiceType());
                serviceObject.setString("url", uri.toString() + "/" + hostName + ":" + statePort);
                serviceObject.setLong("currentGeneration", generation);
            });
            object.setString("url", uri.toString());
            object.setLong("currentGeneration", currentGeneration);
            object.setLong("wantedGeneration", wantedGeneration);
            object.setBool("converged", currentGeneration >= wantedGeneration);
        }
    }

    private static class ServiceResponse extends JSONResponse {

        private ServiceResponse(int status, URI uri, String hostname, Long wantedGeneration) {
            super(status);
            object.setString("url", uri.toString());
            object.setString("host", hostname);
            object.setLong("wantedGeneration", wantedGeneration);
        }

        static ServiceResponse createOkResponse(URI uri, String hostname, Long wantedGeneration, Long currentGeneration, boolean converged) {
            ServiceResponse serviceResponse = new ServiceResponse(200, uri, hostname, wantedGeneration);
            serviceResponse.object.setBool("converged", converged);
            serviceResponse.object.setLong("currentGeneration", currentGeneration);
            return serviceResponse;
        }

        static ServiceResponse createHostNotFoundInAppResponse(URI uri, String hostname, Long wantedGeneration) {
            ServiceResponse serviceResponse = new ServiceResponse(410, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("problem", "Host:port (service) no longer part of application, refetch list of services.");
            return serviceResponse;
        }

        static ServiceResponse createErrorResponse(URI uri, String hostname, Long wantedGeneration, String error) {
            ServiceResponse serviceResponse = new ServiceResponse(500, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("error", error);
            return serviceResponse;
        }

        static ServiceResponse createNotFoundResponse(URI uri, String hostname, Long wantedGeneration, String error) {
            ServiceResponse serviceResponse = new ServiceResponse(404, uri, hostname, wantedGeneration);
            serviceResponse.object.setString("error", error);
            return serviceResponse;
        }
    }

}
