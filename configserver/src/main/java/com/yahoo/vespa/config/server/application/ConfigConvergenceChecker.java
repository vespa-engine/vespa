// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;
import static java.util.stream.Collectors.toList;

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

    private final CloseableHttpClient httpClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ExecutorService executor = createThreadpool();

    @Inject
    public ConfigConvergenceChecker() {
        this.httpClient = createHttpClient();
    }

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
        try {
            if ( ! hostInApplication(application, hostAndPortToCheck))
                return ServiceResponse.createHostNotFoundInAppResponse(requestUrl, hostAndPortToCheck, wantedGeneration);

            long currentGeneration = getServiceGeneration(URI.create("http://" + hostAndPortToCheck), timeout);
            boolean converged = currentGeneration >= wantedGeneration;
            return ServiceResponse.createOkResponse(requestUrl, hostAndPortToCheck, wantedGeneration, currentGeneration, converged);
        } catch (NonSuccessStatusCodeException | IOException e) { // e.g. if we cannot connect to the service to find generation
            return ServiceResponse.createNotFoundResponse(requestUrl, hostAndPortToCheck, wantedGeneration, e.getMessage());
        } catch (Exception e) {
            return ServiceResponse.createErrorResponse(requestUrl, hostAndPortToCheck, wantedGeneration, e.getMessage());
        }
    }

    @Override
    public void deconstruct() {
        try {
            httpClient.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Gets service generation for a list of services (in parallel). */
    private Map<ServiceInfo, Long> getServiceGenerations(List<ServiceInfo> services, Duration timeout) {
        List<Callable<ServiceInfoWithGeneration>> tasks = services.stream()
                .map(service ->
                        (Callable<ServiceInfoWithGeneration>) () -> {
                            long generation;
                            try {
                                generation = getServiceGeneration(URI.create("http://" + service.getHostName()
                                        + ":" + getStatePort(service).get()), timeout);
                            } catch (IOException | NonSuccessStatusCodeException e) {
                                generation = -1L;
                            }
                            return new ServiceInfoWithGeneration(service, generation);
                        })
                .collect(toList());
        try {
            List<Future<ServiceInfoWithGeneration>> taskResults = executor.invokeAll(tasks);
            Map<ServiceInfo, Long> result = new HashMap<>();
            for (Future<ServiceInfoWithGeneration> taskResult : taskResults) {
                ServiceInfoWithGeneration info = taskResult.get();
                result.put(info.service, info.generation);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to retrieve config generation: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to retrieve config generation: " + e.getMessage(), e);
        }
    }

    /** Get service generation of service at given URL */
    private long getServiceGeneration(URI serviceUrl, Duration timeout) throws IOException, NonSuccessStatusCodeException {
        HttpGet request = new HttpGet(createApiUri(serviceUrl));
        request.setConfig(createRequestConfig(timeout));
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) throw new NonSuccessStatusCodeException(statusCode);
            if (response.getEntity() == null) throw new IOException("Response has no content");
            JsonNode jsonContent = jsonMapper.readTree(response.getEntity().getContent());
            return generationFromContainerState(jsonContent);
        } catch (Exception e) {
            log.log(
                    LogLevel.DEBUG,
                    e,
                    () -> String.format("Failed to retrieve service config generation for '%s': %s", serviceUrl, e.getMessage()));
            throw e;
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

    private static URI createApiUri(URI serviceUrl) {
        try {
            return new URIBuilder(serviceUrl)
                    .setPath("/state/v1/config")
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static ExecutorService createThreadpool() {
        return Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory("config-convergence-checker-"));
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder
                .create()
                .setUserAgent("config-convergence-checker")
                .setConnectionTimeToLive(20, TimeUnit.SECONDS)
                .setMaxConnPerRoute(4)
                .setMaxConnTotal(100)
                .setDefaultRequestConfig(createRequestConfig(Duration.ofSeconds(10)))
                .build();
    }

    private static RequestConfig createRequestConfig(Duration timeout) {
        int timeoutMillis = (int)timeout.toMillis();
        return RequestConfig.custom()
                .setConnectionRequestTimeout(timeoutMillis)
                .setConnectTimeout(timeoutMillis)
                .setSocketTimeout(timeoutMillis)
                .build();
    }

    private static class ServiceInfoWithGeneration {
        final ServiceInfo service;
        final long generation;

        ServiceInfoWithGeneration(ServiceInfo service, long generation) {
            this.service = service;
            this.generation = generation;
        }
    }

    private static class NonSuccessStatusCodeException extends Exception {
        final int statusCode;

        NonSuccessStatusCodeException(int statusCode) {
            super("Expected status code 200, got " + statusCode);
            this.statusCode = statusCode;
        }
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
