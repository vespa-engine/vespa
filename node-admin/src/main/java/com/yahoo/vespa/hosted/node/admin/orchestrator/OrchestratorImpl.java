// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesRequestBody;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;
import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.HostSuspensionApi;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.io.IOException;
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

    final Set<HostName> configServerHosts;

    public OrchestratorImpl(Set<HostName> configServerHosts) {
        this.configServerHosts = configServerHosts;
    }



    @Override
    public boolean suspend(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));


        for (HostName configServer : configServerHosts) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + HARDCODED_ORCHESTRATOR_PORT
                        + ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
                HttpClient client = HttpClientBuilder.create().build();
                HttpPut request = new HttpPut(url);
                response = client.execute(request);
            } catch (Exception e) {
                logger.info("Orchestrator communication exception  " + hostName, e);
                continue;
            }
            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return true;
            }

            if (response.getStatusLine().getStatusCode() != 200) {
                logger.info("Orchestrator not 200  " + hostName);
                continue;
            }
            final UpdateHostResponse nodesForHost;
            try {
                nodesForHost = mapper.readValue(response.getEntity().getContent(), UpdateHostResponse.class);
            } catch (IOException e) {
                logger.info("Orchestrator rejected suspend request for host " + hostName, e);
                return false;
            }
            return nodesForHost.reason() == null;
        }
        logger.info("Orchestrator rejected suspend request for host " + hostName, e);
        return false;
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {

        BatchHostSuspendRequest body = new BatchHostSuspendRequest(parentHostName, hostNames);
        for (HostName configServer : configServerHosts) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + HARDCODED_ORCHESTRATOR_PORT
                        + ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API;
                HttpClient client = HttpClientBuilder.create().build();
                HttpPut request = new HttpPut(url);
                request.setEntity(new StringEntity(mapper.writeValueAsString(body)));
                response = client.execute(request);
            } catch (Exception e) {
                continue;
            }
            final BatchOperationResult result =
        try {
            return hostSuspensionClient.apply(hostSuspensionClient -> {
                BatchHostSuspendRequest request = new BatchHostSuspendRequest(parentHostName, hostNames);
                final BatchOperationResult result = hostSuspensionClient.suspendAll(request);
                return result.getFailureReason();
            });
        } catch (ClientErrorException e) {
            if (e instanceof NotFoundException || e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return Optional.empty();
            }
            NODE_ADMIN_LOGGER.info("Orchestrator rejected suspend request for host " + parentHostName, e);
            return Optional.of(e.getLocalizedMessage());
        } catch (IOException e) {
            NODE_ADMIN_LOGGER.warning("Unable to communicate with orchestrator", e);
            return Optional.of("Unable to communicate with orchestrator" + e.getMessage());
        }
    }

    @Override
    public boolean resume(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));
        for (HostName configServer : configServerHosts) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + HARDCODED_ORCHESTRATOR_PORT
                        + ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
                HttpClient client = HttpClientBuilder.create().build();
                HttpDelete request = new HttpDelete(url);
                response = client.execute(request);
            } catch (Exception e) {
                logger.info("Orchestrator communication exception delete " + hostName, e);
                continue;
            }
            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return true;
            }

            if (response.getStatusLine().getStatusCode() != 200) {
                logger.info("Orchestrator not 200  " + hostName);
                continue;
            }
            final UpdateHostResponse nodesForHost;
            try {
                nodesForHost = mapper.readValue(response.getEntity().getContent(), UpdateHostResponse.class);
            } catch (IOException e) {
                logger.info("Orchestrator rejected suspend request for host " + hostName, e);
                return false;
            }
            return nodesForHost.reason() == null;
        }
        logger.info("Orchestrator rejected suspend request for host " + hostName, e);
        return false;
    }

    public static OrchestratorImpl createOrchestratorFromSettings() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }
       // JaxRsStrategy<HostApi> hostApi = jaxRsStrategyFactory.apiWithRetries(HostApi.class, ORCHESTRATOR_PATH_PREFIX_HOST_API);
       // JaxRsStrategy<HostSuspensionApi> suspendApi = jaxRsStrategyFactory.apiWithRetries(HostSuspensionApi.class, ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API);
        return new OrchestratorImpl(configServerHosts);
    }
}
