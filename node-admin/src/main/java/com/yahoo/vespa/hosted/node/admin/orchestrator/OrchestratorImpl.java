// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;

import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.HostSuspensionApi;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;


import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author stiankri
 * @author bakksjo
 * @author dybis
 */
public class OrchestratorImpl implements Orchestrator {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(OrchestratorImpl.class);
    // TODO: Figure out the port dynamically.
    private static final int HARDCODED_ORCHESTRATOR_PORT = 19071;
    // TODO: Find a way to avoid duplicating this (present in orchestrator's services.xml also).
    private static final String ORCHESTRATOR_PATH_PREFIX = "/orchestrator";
    private static final String ORCHESTRATOR_PATH_PREFIX_HOST_API
            = ORCHESTRATOR_PATH_PREFIX + HostApi.PATH_PREFIX;

    private static final String ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API
            = ORCHESTRATOR_PATH_PREFIX + HostSuspensionApi.PATH_PREFIX;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClientBuilder.create().build();

    final Set<HostName> configServerHosts;

    public OrchestratorImpl(Set<HostName> configServerHosts) {
        this.configServerHosts = configServerHosts;
    }

    interface CreateRequest {
        HttpUriRequest createRequest(HostName configserver);
    }

    // return value null means not found on server.
    private <T extends  Object> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType)  {
        for (HostName configServer : configServerHosts) {
            final HttpResponse response;
            try {
                response = client.execute(requestFactory.createRequest(configServer));
            } catch (Exception e) {
                NODE_ADMIN_LOGGER.info("Exception while talking to " + configServer + "(will try all config servers)", e);
                continue;
            }
            if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return null;
            }
            try {
                return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
            } catch (IOException e) {
                throw new RuntimeException("Response didn't contain nodes element, failed parsing?", e);
            }
        }
        throw new RuntimeException("Did not get any positive answer");
    }

    @Override
    public boolean suspend(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));

        final UpdateHostResponse updateHostResponse = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + HARDCODED_ORCHESTRATOR_PORT
                    + ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
            return new HttpPut(url);
        }, UpdateHostResponse.class);

        if (updateHostResponse == null) {
            // Orchestrator doesn't care about this node, so don't let that stop us.
            return true;
        }
        return updateHostResponse.reason() == null;
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        final BatchOperationResult batchOperationResult = tryAllConfigServers(configserver -> {
            BatchHostSuspendRequest body = new BatchHostSuspendRequest(parentHostName, hostNames);
            String url = "http://" + configserver + ":" + HARDCODED_ORCHESTRATOR_PORT
                    + ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API;
            HttpPut request = new HttpPut(url);
            try {
                request.setEntity(new StringEntity(mapper.writeValueAsString(body)));
            } catch (UnsupportedEncodingException|JsonProcessingException e) {
                throw new RuntimeException("Failed creating request", e);
            }
            return request;
        }, BatchOperationResult.class);
        return batchOperationResult.getFailureReason();
    }

    @Override
    public boolean resume(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));

        final UpdateHostResponse batchOperationResult = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + HARDCODED_ORCHESTRATOR_PORT
                    + ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
            return new HttpDelete(url);
        }, UpdateHostResponse.class);

        if (batchOperationResult == null) {
            // Orchestrator doesn't care about this node, so don't let that stop us.
            return true;
        }
        return batchOperationResult.reason() == null;
    }

    public static OrchestratorImpl createOrchestratorFromSettings() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }
        return new OrchestratorImpl(configServerHosts);
    }
}
