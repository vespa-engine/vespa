// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.ConfigRequestHostLivenessTracker;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.MockTenantProvider;

import java.util.Optional;
import java.util.concurrent.CompletionService;

/**
 * Test utility mocking an RPC server.
 *
 * @author Ulf Lilleengen
 */
public class MockRpc extends RpcServer {

    public boolean forced = false;
    public RuntimeException exception = null;
    public int errorCode = 0;
    public ConfigResponse response = null;

    // Fields used to assert on the calls made to this from tests
    public boolean tryResolveConfig = false;
    public boolean tryRespond = false;
    /** The last request received and responded to */
    public volatile JRTServerConfigRequest latestRequest = null;


    public MockRpc(int port, boolean createDefaultTenant, boolean pretendToHaveLoadedAnyApplication) {
        super(createConfig(port), null, Metrics.createTestMetrics(), 
              new HostRegistries(), new ConfigRequestHostLivenessTracker(), new FileServer(Files.createTempDir()));
        if (createDefaultTenant) {
            onTenantCreate(TenantName.from("default"), new MockTenantProvider(pretendToHaveLoadedAnyApplication));
        }
    }

    public MockRpc(int port, boolean createDefaultTenant) {
        this(port, createDefaultTenant, true);
    }

    public MockRpc(int port) {
        this(port, true);
    }

    /** Reset fields used to assert on the calls made to this */
    public void resetChecks() {
        forced = false;
        tryResolveConfig = false;
        tryRespond = false;
        latestRequest = null;
    }

    private static ConfigserverConfig createConfig(int port) {
        ConfigserverConfig.Builder b = new ConfigserverConfig.Builder();
        b.rpcport(port);
        return new ConfigserverConfig(b);
    }

    public boolean waitUntilSet(int timeout) {
        long start = System.currentTimeMillis();
        long end = start + timeout;
        while (start < end) {
            if (latestRequest != null)
                return true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public Boolean addToRequestQueue(JRTServerConfigRequest request, boolean forceResponse, CompletionService<Boolean> completionService) {
        latestRequest = request;
        forced = forceResponse;
        return true;
    }

    @Override
    public void respond(JRTServerConfigRequest request) {
        latestRequest = request;
        tryRespond = true;
        errorCode = request.errorCode();
    }

    @Override
    public ConfigResponse resolveConfig(JRTServerConfigRequest request, GetConfigContext context, Optional<Version> vespaVersion) {
        tryResolveConfig = true;
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
