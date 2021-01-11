// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.ConfigRequestHostLivenessTracker;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.security.NoopRpcAuthorizer;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionService;

/**
 * Test utility mocking an RPC server.
 *
 * @author Ulf Lilleengen
 */
public class MockRpcServer extends RpcServer {

    public RuntimeException exception = null;
    public int errorCode = 0;
    public final ConfigResponse response = null;

    /** The last request received and responded to */
    public volatile JRTServerConfigRequest latestRequest = null;


    public MockRpcServer(int port, File tempDir) {
        super(createConfig(port),
              null,
              Metrics.createTestMetrics(),
              new HostRegistries(),
              new ConfigRequestHostLivenessTracker(),
              new FileServer(tempDir),
              new NoopRpcAuthorizer(),
              new RpcRequestHandlerProvider());
    }

    private static ConfigserverConfig createConfig(int port) {
        ConfigserverConfig.Builder b = new ConfigserverConfig.Builder();
        b.rpcport(port);
        return new ConfigserverConfig(b);
    }

    void waitUntilSet(Duration timeout) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (latestRequest != null)
                return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Boolean addToRequestQueue(JRTServerConfigRequest request, boolean forceResponse, CompletionService<Boolean> completionService) {
        latestRequest = request;
        return true;
    }

    @Override
    public void respond(JRTServerConfigRequest request) {
        latestRequest = request;
        errorCode = request.errorCode();
    }

    @Override
    public ConfigResponse resolveConfig(JRTServerConfigRequest request, GetConfigContext context, Optional<Version> vespaVersion) {
        if (exception != null) {
            throw exception;
        }
        return response;
    }

    @Override
    public boolean isHostedVespa() { return true; }

    @Override
    public boolean allTenantsLoaded() { return true; }

    @Override
    public boolean isRunning() {
        return true;
    }

}
