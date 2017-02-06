// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Checks for convergence of config generation for a given application.
 *
 * @author lulf
 * @author hmusum
 */
public class ApplicationConvergenceChecker extends AbstractComponent {
    private static final String statePath = "/state/v1/";
    private static final String configSubPath = "config";
    private final StateApiFactory stateApiFactory;
    private final Client client = ClientBuilder.newClient();

    private final static Set<String> serviceTypes = new HashSet<>(Arrays.asList(
            "container",
            "container-clustercontroller",
            "qrserver",
            "docprocservice",
            "searchnode",
            "storagenode",
            "distributor"
    ));

    @Inject
    public ApplicationConvergenceChecker() {
        this(ApplicationConvergenceChecker::createStateApi);
    }

    public ApplicationConvergenceChecker(StateApiFactory stateApiFactory) {
        this.stateApiFactory = stateApiFactory;
    }

    public ServiceListResponse serviceListToCheckForConfigConvergence(Application application, URI uri) {
        List<Service> services = new ArrayList<>();
        Long wantedGeneration = application.getApplicationGeneration();
        try {
            // Note: Uses latest config model version to get config
            ModelConfig config = application.getConfig(ModelConfig.class, "");
            config.hosts()
                  .forEach(host -> host.services().stream()
                                       .filter(service -> serviceTypes.contains(service.type()))
                                       .forEach(service -> getStatePort(service).ifPresent(
                                               port -> services.add(new Service(host.name(), port, service.type())))));
            return new ServiceListResponse(200, services, uri, wantedGeneration);
        } catch (IOException e) {
            return new ServiceListResponse(500, services, uri, wantedGeneration);
        }
    }

    public ServiceResponse serviceConvergenceCheck(Application application, String hostAndPortToCheck, URI uri) {
        Long wantedGeneration = application.getApplicationGeneration();
        if ( ! hostInApplication(application, hostAndPortToCheck))
            return ServiceResponse.createHostNotFoundInAppResponse(uri, hostAndPortToCheck, wantedGeneration);

        try {
            long currentGeneration = getServiceGeneration(URI.create("http://" + hostAndPortToCheck));
            boolean converged = currentGeneration >= wantedGeneration;
            return ServiceResponse.createOkResponse(uri, hostAndPortToCheck, wantedGeneration, currentGeneration, converged);
        } catch (ProcessingException e) { // e.g. if we cannot connect to the service to find generation
            return ServiceResponse.createNotFoundResponse(uri, hostAndPortToCheck, wantedGeneration, e.getMessage());
        } catch (Exception e) {
            return ServiceResponse.createErrorResponse(uri, hostAndPortToCheck, wantedGeneration, e.getMessage());
        }
    }

    @Override
    public void deconstruct() {
        client.close();
    }

    @Path(statePath)
    public interface StateApi {
        @Path(configSubPath)
        @GET
        JsonNode config();
    }

    public interface StateApiFactory {
        StateApi createStateApi(Client client, URI serviceUri);
    }

    private Optional<Integer> getStatePort(ModelConfig.Hosts.Services service) { return service.ports().stream()
                .filter(port -> port.tags().contains("state"))
                .map(ModelConfig.Hosts.Services.Ports::number)
                .findFirst();
    }

    private long generationFromContainerState(JsonNode state) {
        return state.get("config").get("generation").asLong();
    }

    private static StateApi createStateApi(Client client, URI uri) {
        WebTarget target = client.target(uri);
        return WebResourceFactory.newResource(StateApi.class, target);
    }

    private long getServiceGeneration(URI serviceUri) {
        StateApi state = stateApiFactory.createStateApi(client, serviceUri);
        return generationFromContainerState(state.config());
    }

    private boolean hostInApplication(Application application, String hostPort) {
        final ModelConfig config;
        try {
            config = application.getConfig(ModelConfig.class, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final List<ModelConfig.Hosts> hosts = config.hosts();
        for (ModelConfig.Hosts host : hosts) {
            if (hostPort.startsWith(host.name())) {
                for (ModelConfig.Hosts.Services service : host.services()) {
                    for (ModelConfig.Hosts.Services.Ports port : service.ports()) {
                        if (hostPort.equals(host.name() + ":" + port.number())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static class ServiceListResponse extends JSONResponse {
        final Cursor debug;

        private ServiceListResponse(int status, List<Service> services, URI uri, Long wantedGeneration) {
            super(status);
            Cursor serviceArray = object.setArray("services");
            for (Service s : services) {
                Cursor service = serviceArray.addObject();
                service.setString("host", s.hostname);
                service.setLong("port", s.port);
                service.setString("type", s.type);
                service.setString("url", uri.toString() + "/" + s.hostname + ":" + s.port);
            }
            debug = object.setObject("debug");
            object.setString("url", uri.toString());
            debug.setLong("wantedGeneration", wantedGeneration);
        }
    }

    static class ServiceResponse extends JSONResponse {
        final Cursor debug;

        private ServiceResponse(int status, URI uri, String hostname, Long wantedGeneration) {
            super(status);
            debug = object.setObject("debug");
            object.setString("url", uri.toString());
            debug.setString("host", hostname);
            debug.setLong("wantedGeneration", wantedGeneration);
        }

        static ServiceResponse createOkResponse(URI uri, String hostname, Long wantedGeneration, Long currentGeneration, boolean converged) {
            ServiceResponse serviceResponse = new ServiceResponse(200, uri, hostname, wantedGeneration);
            serviceResponse.object.setBool("converged", converged);
            serviceResponse.debug.setLong("currentGeneration", currentGeneration);
            return serviceResponse;
        }

        static ServiceResponse createHostNotFoundInAppResponse(URI uri, String hostname, Long wantedGeneration) {
            ServiceResponse serviceResponse = new ServiceResponse(410, uri, hostname, wantedGeneration);
            serviceResponse.debug.setString("problem", "Host:port (service) no longer part of application, refetch list of services.");
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

    private static class Service {
        private final String hostname;
        private final int port;
        private final String type;

        private Service(String hostname, int port, String type) {
            this.hostname = hostname;
            this.port = port;
            this.type = type;
        }
    }

}
