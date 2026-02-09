// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.json.Jackson;
import ai.vespa.util.http.hc5.VespaAsyncHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static java.util.logging.Level.FINE;

/**
 * @author glebashnik
 */
public class ConfigStateChecker extends AbstractComponent {
    private static final Logger log = Logger.getLogger(ConfigStateChecker.class.getName());

    private static final Set<String> serviceTypesToCheck = Set.of(
            CONTAINER.serviceName,
            LOGSERVER_CONTAINER.serviceName,
            CLUSTERCONTROLLER_CONTAINER.serviceName,
            METRICS_PROXY_CONTAINER.serviceName,
            "searchnode",
            "storagenode",
            "distributor");

    private final ExecutorService responseHandlerExecutor =
            Executors.newSingleThreadExecutor(new DaemonThreadFactory("config-state-checker-response-handler-"));

    /**
     * Fetches config states of all services in {@code application} with {@code hostnames}.
     */
    public Map<ServiceInfo, ServiceConfigState> getServiceConfigStates(
            Application application, Duration timeoutPerService, Set<String> hostnames) {
        List<ServiceInfo> servicesToCheck = application.getModel().getHosts().stream()
                .flatMap(host -> host.getServices().stream()
                        .filter(service -> hostnames.contains(host.getHostname())
                                && serviceTypesToCheck.contains(service.getServiceType())
                                && getStatePort(service).isPresent()))
                .toList();

        log.log(FINE, () -> "Services to check for config state: " + servicesToCheck);
        return getServiceConfigStates(servicesToCheck, timeoutPerService);
    }

    /**
     * Fetch service config states for a list of services (in parallel).
     */
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
                                                "Failed to retrieve service config state for '%s': %s",
                                                service, error.getMessage()));
                                temporaryResult.put(service, new ServiceConfigState(-1L, Optional.empty()));
                            }
                            return null;
                        });
                inprogressRequests.add(inprogressRequest);
            }
            CompletableFuture.allOf(inprogressRequests.toArray(CompletableFuture[]::new))
                    .join();
            return createMapOrderedByServiceList(services, temporaryResult);
        } catch (IOException e) {
            // Actual client implementation does not throw IOException on close()
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get service generation of service at given URL
     */
    private CompletableFuture<ServiceConfigState> getServiceConfigState(
            CloseableHttpAsyncClient client, URI serviceUrl, Duration timeout) {
        SimpleHttpRequest request =
                SimpleRequestBuilder.get(createApiUri(serviceUrl)).build();
        request.setConfig(createRequestConfig(timeout));

        // Ignoring returned Future object as we want to use the more flexible CompletableFuture instead
        CompletableFuture<SimpleHttpResponse> responsePromise = new CompletableFuture<>();
        client.execute(request, new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                responsePromise.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                responsePromise.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                responsePromise.cancel(false);
            }
        });

        // Don't do JSON parsing in http client's thread.
        return responsePromise.thenApplyAsync(ConfigStateChecker::handleResponse, responseHandlerExecutor);
    }

    public void deconstruct() {
        responseHandlerExecutor.shutdown();
        try {
            responseHandlerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Unable to shutdown executor", e);
        }
    }

    static ServiceConfigState handleResponse(SimpleHttpResponse response) throws UncheckedIOException {
        try {
            int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK) throw new IOException("Expected status code 200, got " + statusCode);
            if (response.getBody() == null) throw new IOException("Response has no content");
            return serviceConfigStateFromJson(Jackson.mapper().readTree(response.getBodyText()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<Integer> getStatePort(ServiceInfo service) {
        return service.getPorts().stream()
                .filter(port -> port.getTags().contains("state"))
                .map(PortInfo::getPort)
                .findFirst();
    }

    private static ServiceConfigState serviceConfigStateFromJson(JsonNode state) {
        JsonNode configNode = state.get("config");
        long generation = configNode.get("generation").asLong(-1);
        Optional<Boolean> applyOnRestart = configNode.has("applyOnRestart")
                ? Optional.of(configNode.get("applyOnRestart").asBoolean())
                : Optional.empty();
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
            return new URIBuilder(serviceUrl).setPath("/state/v1/config").build();
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
        return VespaAsyncHttpClientBuilder.create(tlsStrategy -> PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(100)
                        .setMaxConnPerRoute(10)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setTimeToLive(TimeValue.ofMilliseconds(1))
                                .setConnectTimeout(
                                        Timeout.ofSeconds(10)) // Times out at 1s over wireguard with 500+ services.
                                .build())
                        .setTlsStrategy(tlsStrategy)
                        .build())
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(2))
                        .build())
                .setUserAgent("config-state-checker")
                .build();
    }
}
