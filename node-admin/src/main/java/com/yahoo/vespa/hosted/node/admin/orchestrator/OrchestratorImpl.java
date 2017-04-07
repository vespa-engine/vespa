// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.defaults.Defaults;

import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;

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
    public void suspend(final String hostName) {
        UpdateHostResponse response;
        try {
            response = requestExecutor.put(getSuspendPath(hostName),
                    WEB_SERVICE_PORT,
                    Optional.empty(), /* body */
                    UpdateHostResponse.class);
        } catch (ConfigServerHttpRequestExecutor.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to suspend " + hostName + ", host not found");
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
                    WEB_SERVICE_PORT,
                    Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                    BatchOperationResult.class);
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
            response = requestExecutor.delete(path, WEB_SERVICE_PORT, UpdateHostResponse.class);
        } catch (ConfigServerHttpRequestExecutor.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to resume " + hostName + ", host not found");
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
