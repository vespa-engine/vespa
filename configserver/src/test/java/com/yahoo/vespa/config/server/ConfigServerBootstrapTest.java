// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.config.server.tenant.TenantRequestHandler;
import com.yahoo.vespa.config.server.tenant.TestWithTenant;
import com.yahoo.vespa.config.server.version.VersionState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigServerBootstrapTest extends TestWithTenant {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testConfigServerBootstrap() throws Exception {
        File versionFile = temporaryFolder.newFile();
        ConfigserverConfig.Builder config = new ConfigserverConfig.Builder();
        MockTenantRequestHandler myServer = new MockTenantRequestHandler(Metrics.createTestMetrics());
        MockRpc rpc = new MockRpc(new ConfigserverConfig(config).rpcport());

        assertFalse(myServer.started);
        assertFalse(myServer.stopped);
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tenants, rpc, (application, timeout) -> Optional.empty(), versionState);
        waitUntilStarted(rpc, 60000);
        assertFalse(versionState.isUpgraded());
        assertThat(versionState.currentVersion(), is(versionState.storedVersion()));
        assertThat(IOUtils.readAll(new FileReader(versionFile)), is(versionState.currentVersion().toSerializedForm()));
        assertTrue(rpc.started);
        assertFalse(rpc.stopped);
        bootstrap.deconstruct();
        assertTrue(rpc.started);
        assertTrue(rpc.stopped);
    }

    private void waitUntilStarted(MockRpc server, long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeout) {
            if (server.started)
                return;
            Thread.sleep(10);
        }
    }

    public static class MockTenantRequestHandler extends TenantRequestHandler {
        public volatile boolean started = false;
        public volatile boolean stopped = false;

        public MockTenantRequestHandler(Metrics statistics) {
            super(statistics, TenantName.from("testTenant"), new ArrayList<>(), new UncompressedConfigResponseFactory(), new HostRegistries());
        }
    }

    public static class MockRpc extends com.yahoo.vespa.config.server.rpc.MockRpc {
        public volatile boolean started = false;
        public volatile boolean stopped = false;

        public MockRpc(int port) {
            super(port);
        }

        @Override
        public void run() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }
}
