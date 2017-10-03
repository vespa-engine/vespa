// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;

import com.yahoo.vespa.hosted.node.admin.util.HttpException;
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
    // TODO: Find a way to avoid duplicating this (present in orchestrator's services.xml also).
    private static final String ORCHESTRATOR_PATH_PREFIX = "/orchestrator";
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_API
            = ORCHESTRATOR_PATH_PREFIX + HostApi.PATH_PREFIX;
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API
            = ORCHESTRATOR_PATH_PREFIX + HostSuspensionApi.PATH_PREFIX;

    private final ConfigServerHttpRequestExecutor requestExecutor;
    private final int port;

    public OrchestratorImpl(ConfigServerHttpRequestExecutor requestExecutor, int port) {
        this.requestExecutor = requestExecutor;
        this.port = port;
    }

    @Override
    public void suspend(final String hostName) {
        UpdateHostResponse response;
        try {
            response = requestExecutor.put(getSuspendPath(hostName),
                    port,
                    Optional.empty(), /* body */
                    UpdateHostResponse.class);
        } catch (HttpException.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to suspend " + hostName + ", host not found");
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to suspend " + hostName + ": " +
                    e.toString());
        } catch (Exception e) {
            throw new RuntimeException("Got error on suspend", e);
        }

        Optional.ofNullable(response.reason()).ifPresent(reason -> {
            throw new OrchestratorException(reason.message());
        });
    }

    @Override
    public void suspend(String parentHostName, List<String> hostNames) {
        final BatchOperationResult batchOperationResult;
        try {
            batchOperationResult = requestExecutor.put(
                    ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                    port,
                    Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                    BatchOperationResult.class);
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to batch suspend for " +
                    parentHostName + ": " + e.toString());
        } catch (Exception e) {
            throw new RuntimeException("Got error on batch suspend for " + parentHostName + ", with nodes " + hostNames, e);
        }

        batchOperationResult.getFailureReason().ifPresent(reason -> {
            throw new OrchestratorException(reason);
        });
    }

    @Override
    public void resume(final String hostName) {
        UpdateHostResponse response;
        try {
            String path = getSuspendPath(hostName);
            response = requestExecutor.delete(path, port, UpdateHostResponse.class);
        } catch (HttpException.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to resume " + hostName + ", host not found");
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to suspend " + hostName + ": " +
                    e.toString());
        } catch (Exception e) {
            throw new RuntimeException("Got error on resume", e);
        }

        Optional.ofNullable(response.reason()).ifPresent(reason -> {
            throw new OrchestratorException(reason.message());
        });
    }

    private String getSuspendPath(String hostName) {
        return ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended";
    }

}
