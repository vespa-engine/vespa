// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
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

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

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


    private final JaxRsStrategy<HostApi> hostApiClient;
    private final JaxRsStrategy<HostSuspensionApi> hostSuspensionClient;


    public OrchestratorImpl(JaxRsStrategy<HostApi> hostApiClient, JaxRsStrategy<HostSuspensionApi> hostSuspensionClient) {
        this.hostApiClient = hostApiClient;
        this.hostSuspensionClient = hostSuspensionClient;
    }

    @Override
    public boolean suspend(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));

        try {
            return hostApiClient.apply(api -> {
                final UpdateHostResponse response = api.suspend(hostName.s());
                return response.reason() == null;
            });
        } catch (ClientErrorException e) {
            if (e instanceof NotFoundException || e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return true;
            }
            logger.log(Level.INFO, "Orchestrator rejected suspend request for host " + hostName, e);
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to communicate with orchestrator", e);
            return false;
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
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
            NODE_ADMIN_LOGGER.log(Level.INFO, "Orchestrator rejected suspend request for host " + parentHostName, e);
            return Optional.of(e.getLocalizedMessage());
        } catch (IOException e) {
            NODE_ADMIN_LOGGER.log(Level.WARNING, "Unable to communicate with orchestrator", e);
            return Optional.of("Unable to communicate with orchestrator" + e.getMessage());
        }
    }

    @Override
    public boolean resume(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));

        try {
            final boolean resumeSucceeded = hostApiClient.apply(api -> {
                final UpdateHostResponse response = api.resume(hostName.s());
                return response.reason() == null;
            });
            return resumeSucceeded;
        } catch (ClientErrorException e) {
            logger.log(Level.INFO, "Orchestrator rejected resume request for host " + hostName, e);
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to communicate with orchestrator", e);
            return false;
        }
    }

    public static OrchestratorImpl createOrchestratorFromSettings() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }
        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory jaxRsStrategyFactory = new JaxRsStrategyFactory(
                configServerHosts, HARDCODED_ORCHESTRATOR_PORT, jaxRsClientFactory);
        JaxRsStrategy<HostApi> hostApi = jaxRsStrategyFactory.apiWithRetries(HostApi.class, ORCHESTRATOR_PATH_PREFIX_HOST_API);
        JaxRsStrategy<HostSuspensionApi> suspendApi = jaxRsStrategyFactory.apiWithRetries(HostSuspensionApi.class, ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API);
        return new OrchestratorImpl(hostApi, suspendApi);
    }
}
