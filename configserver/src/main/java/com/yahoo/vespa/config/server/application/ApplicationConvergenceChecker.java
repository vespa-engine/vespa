// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Checks for convergence of config generation for a given application.
 *
 * @author lulf
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

    public HttpResponse listConfigConvergence(Application application, URI uri) {
        final JSONObject answer = new JSONObject();
        final JSONArray nodes = new JSONArray();
        final ModelConfig config;
        try {
            config = application.getConfig(ModelConfig.class, "");
        } catch (IOException e) {
            throw new RuntimeException("failed on get config model", e);
        }
        config.hosts()
              .forEach(host -> {
                  host.services().stream()
                      .filter(service -> serviceTypes.contains(service.type()))
                      .forEach(service -> {
                          Optional<Integer> statePort = getStatePort(service);
                          if (statePort.isPresent()) {
                              JSONObject hostNode = new JSONObject();
                              try {
                                  hostNode.put("host", host.name());
                                  hostNode.put("port", statePort.get());
                                  hostNode.put("url", uri.toString() + "/" + host.name() + ":" + statePort.get());
                                  hostNode.put("type", service.type());

                              } catch (JSONException e) {
                                  throw new RuntimeException(e);
                              }
                              nodes.put(hostNode);
                          }
                      });
              });
        try {
            answer.put("services", nodes);
            JSONObject debug = new JSONObject();
            debug.put("wantedVersion", application.getApplicationGeneration());
            answer.put("debug", debug);
            answer.put("url", uri.toString());
            return new JsonHttpResponse(200, answer);
        } catch (JSONException e) {
            try {
                answer.put("error", e.getMessage());
            } catch (JSONException e1) {
                throw new RuntimeException("Failed while creating error message ", e1);
            }
            return new JsonHttpResponse(500, answer);
        }
    }

    public HttpResponse nodeConvergenceCheck(Application application, String hostFromRequest, URI uri) {
        JSONObject answer = new JSONObject();
        JSONObject debug = new JSONObject();
        try {
            answer.put("url", uri);
            debug.put("wantedGeneration", application.getApplicationGeneration());
            debug.put("host", hostFromRequest);

            if (!hostInApplication(application, hostFromRequest)) {
                debug.put("problem", "Host:port (service) no longer part of application, refetch list of services.");
                answer.put("debug", debug);
                return new JsonHttpResponse(410, answer);
            }
            final long generation = getServiceGeneration(URI.create("http://" + hostFromRequest));
            debug.put("currentGeneration", generation);
            answer.put("debug", debug);
            answer.put("converged", generation >= application.getApplicationGeneration());
            return new JsonHttpResponse(200, answer);
        } catch (JSONException | ProcessingException e) {
            try {
                answer.put("error", e.getMessage());
            } catch (JSONException e1) {
                throw new RuntimeException("Fail while creating error message ", e1);
            }
            return new JsonHttpResponse(500, answer);
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

    private static class JsonHttpResponse extends HttpResponse {

        private final JSONObject answer;

        JsonHttpResponse(int returnCode, JSONObject answer) {
            super(returnCode);
            this.answer = answer;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(answer.toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getContentType() {
            return HttpConfigResponse.JSON_CONTENT_TYPE;
        }
    }

}
