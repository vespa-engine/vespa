// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.ConnectionException;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author stiankri
 * @author bakksjo
 * @author dybis
 */
public class OrchestratorImpl implements Orchestrator {
    private static final Logger logger = Logger.getLogger(OrchestratorImpl.class.getName());

    // The server-side Orchestrator has an internal timeout of 10s.
    //
    // Note: A 409 has been observed to be returned after 33s in a case possibly involving
    // zk leader election (which is unfortunate as it is difficult to differentiate between
    // transient timeouts (do not allow suspend on timeout) and the config server being
    // permanently down (allow suspend)). For now we'd like to investigate such long
    // requests so keep the timeout low(er).
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(15);

    // TODO: Find a way to avoid duplicating this (present in orchestrator's services.xml also).
    private static final String ORCHESTRATOR_PATH_PREFIX = "/orchestrator";
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_API
            = ORCHESTRATOR_PATH_PREFIX + "/v1/hosts";
    static final String ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API
            = ORCHESTRATOR_PATH_PREFIX + "/v1/suspensions/hosts";

    private final ConfigServerApi configServerApi;

    public OrchestratorImpl(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public void suspend(final String hostName) {
        UpdateHostResponse response;
        try {
            var params = new ConfigServerApi
                    .Params<UpdateHostResponse>()
                    .setConnectionTimeout(CONNECTION_TIMEOUT)
                    .setRetryPolicy(createRetryPolicyForSuspend());
            response = configServerApi.put(getSuspendPath(hostName), Optional.empty(), UpdateHostResponse.class, params);
        } catch (HttpException.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to suspend " + hostName + ", host not found");
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to suspend " + hostName + ": " + e);
        } catch (ConnectionException e) {
            throw ConvergenceException.ofTransient("Failed to suspend " + hostName + ": " + e.getMessage());
        } catch (RuntimeException e) {
            throw new RuntimeException("Got error on suspend", e);
        }

        Optional.ofNullable(response.reason()).ifPresent(reason -> {
            throw new OrchestratorException(reason.message());
        });
    }

    private static ConfigServerApi.RetryPolicy<UpdateHostResponse> createRetryPolicyForSuspend() {
        return new ConfigServerApi.RetryPolicy<>() {
            @Override
            public boolean tryNextConfigServer(URI configServerEndpoint, UpdateHostResponse response) {
                HostStateChangeDenialReason reason = response.reason();
                if (reason == null) {
                    return false;
                }

                // The config server has likely just bootstrapped, so try the next.
                if ("unknown-service-status".equals(reason.constraintName())) {
                    // Warn for now and until this feature has proven to work well
                    logger.warning("Config server at [" + configServerEndpoint +
                                   "] failed with transient error (will try next): " +
                                   reason.message());

                    return true;
                }

                return false;
            }
        };
    }

    @Override
    public void suspend(String parentHostName, List<String> hostNames) {
        final BatchOperationResult batchOperationResult;
        try {
            var params = new ConfigServerApi.Params<BatchOperationResult>().setConnectionTimeout(CONNECTION_TIMEOUT);
            String hostnames = String.join("&hostname=", hostNames);
            String url = String.format("%s/%s?hostname=%s", ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                                       parentHostName, hostnames);
            batchOperationResult = configServerApi.put(url, Optional.empty(), BatchOperationResult.class, params);
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to batch suspend for " + parentHostName + ": " + e);
        } catch (ConnectionException e) {
            throw ConvergenceException.ofTransient("Failed to batch suspend for " + parentHostName + ": " + e.getMessage());
        } catch (RuntimeException e) {
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
            response = configServerApi.delete(path, UpdateHostResponse.class);
        } catch (HttpException.NotFoundException n) {
            throw new OrchestratorNotFoundException("Failed to resume " + hostName + ", host not found");
        } catch (HttpException e) {
            throw new OrchestratorException("Failed to resume " + hostName + ": " + e);
        } catch (ConnectionException e) {
            throw ConvergenceException.ofTransient("Failed to resume " + hostName + ": " + e.getMessage());
        } catch (RuntimeException e) {
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
