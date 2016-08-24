// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;

import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.HostSuspensionApi;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.applicationmodel.HostName;
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
    private final ConfigServerHttpRequestExecutor requestExecutor;

    private static final String ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API
            = ORCHESTRATOR_PATH_PREFIX + HostSuspensionApi.PATH_PREFIX;

    public OrchestratorImpl(ConfigServerHttpRequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    @Override
    public boolean suspend(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));

        try {
            final UpdateHostResponse updateHostResponse = requestExecutor.put(
                    ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended",
                    HARDCODED_ORCHESTRATOR_PORT,
                    Optional.empty(), /* body */
                    UpdateHostResponse.class);

            if (updateHostResponse == null) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return true;
            }
            return updateHostResponse.reason() == null;
        } catch (Exception e) {
            logger.info("Got error on suspend " + hostName, e);
            return false;
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        try {
            final BatchOperationResult batchOperationResult = requestExecutor.put(
                    ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                    HARDCODED_ORCHESTRATOR_PORT,
                    Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                    BatchOperationResult.class);
            return batchOperationResult.getFailureReason();
        } catch (Exception e) {
            return Optional.of(e.getMessage());
        }
    }

    @Override
    public boolean resume(final HostName hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName.toString()));
        try {
            final UpdateHostResponse batchOperationResult = requestExecutor.delete(
                    ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended",
                    HARDCODED_ORCHESTRATOR_PORT,
                    UpdateHostResponse.class);
            if (batchOperationResult == null) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                logger.info("Got not found on delete, resuming");
                return true;
            }
            return batchOperationResult.reason() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static OrchestratorImpl createOrchestratorFromSettings() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }
        return new OrchestratorImpl(new ConfigServerHttpRequestExecutor(configServerHosts));
    }
}
