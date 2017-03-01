// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;

import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.HostSuspensionApi;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import java.util.List;
import java.util.Optional;

/**
 * @author stiankri
 * @author bakksjo
 * @author dybis
 */
public class OrchestratorImpl implements Orchestrator {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(OrchestratorImpl.class);
    static final int WEB_SERVICE_PORT = Defaults.getDefaults().vespaWebServicePort();
    // TODO: Find a way to avoid duplicating this (present in orchestrator's services.xml also).
    private static final String ORCHESTRATOR_PATH_PREFIX = "/orchestrator";
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_API
            = ORCHESTRATOR_PATH_PREFIX + HostApi.PATH_PREFIX;
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API
            = ORCHESTRATOR_PATH_PREFIX + HostSuspensionApi.PATH_PREFIX;

    private final ConfigServerHttpRequestExecutor requestExecutor;

    public OrchestratorImpl(ConfigServerHttpRequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    @Override
    public boolean suspend(final String hostName) {
        PrefixLogger logger = getLogger(hostName);
        try {
            String path = getSuspendPath(hostName);
            UpdateHostResponse response = requestExecutor.put(path,
                                                              WEB_SERVICE_PORT,
                                                              Optional.empty(), /* body */
                                                              UpdateHostResponse.class);
            return response.reason() == null;
        } catch (ConfigServerHttpRequestExecutor.NotFoundException n) {
            // Orchestrator doesn't care about this node, so don't let that stop us.
            logger.info("Got not found on suspending, allowed to suspend");
            return true;
        } catch (Exception e) {
            logger.info("Got error on suspend", e);
            return false;
        }
    }

    @Override
    public Optional<String> suspend(String parentHostName, List<String> hostNames) {
        try {
            final BatchOperationResult batchOperationResult = requestExecutor.put(
                    ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                    WEB_SERVICE_PORT,
                    Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                    BatchOperationResult.class);
            return batchOperationResult.getFailureReason();
        } catch (Exception e) {
            NODE_ADMIN_LOGGER.info("Got error on batch suspend for " + parentHostName + ", with nodes " + hostNames, e);
            return Optional.of(e.getMessage());
        }
    }

    @Override
    public boolean resume(final String hostName) {
        PrefixLogger logger = getLogger(hostName);
        try {
            String path = getSuspendPath(hostName);
            UpdateHostResponse response = requestExecutor.delete(path, WEB_SERVICE_PORT, UpdateHostResponse.class);
            return response.reason() == null;
        } catch (ConfigServerHttpRequestExecutor.NotFoundException n) {
            // Orchestrator doesn't care about this node, so don't let that stop us.
            logger.info("Got not found on resuming, allowed to resume");
            return true;
        } catch (Exception e) {
            logger.info("Got error on resume", e);
            return false;
        }
    }

    private PrefixLogger getLogger(String hostName) {
        return PrefixLogger.getNodeAgentLogger(OrchestratorImpl.class, NodeRepositoryImpl.containerNameFromHostName(hostName));
    }

    private String getSuspendPath(String hostName) {
        return ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
    }

}
