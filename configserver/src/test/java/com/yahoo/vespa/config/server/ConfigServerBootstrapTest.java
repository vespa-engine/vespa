// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.deploy.MockDeployer;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.http.v2.SessionActiveHandlerTest;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRequestHandler;
import com.yahoo.vespa.config.server.tenant.TestWithTenant;
import com.yahoo.vespa.config.server.version.VersionState;
import org.hamcrest.core.Is;
import org.junit.Before;
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
    private final TenantName tenant1 = TenantName.from("tenant1");
    private final TenantName tenant2 = TenantName.from("tenant2");

    private ApplicationRepository applicationRepository;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        tenants.writeTenantPath(tenant1);
        tenants.writeTenantPath(tenant2);

        applicationRepository = new ApplicationRepository(tenants,
                                                          HostProvisionerProvider.withProvisioner(new SessionActiveHandlerTest.MockProvisioner()),
                                                          curator,
                                                          new LogServerLogGrabber(),
                                                          new ApplicationConvergenceChecker(),
                                                          new HttpProxy(new SimpleHttpFetcher()),
                                                          new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

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
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(applicationRepository, rpc, (application, timeout) -> Optional.empty(), versionState);
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

    @Test
    public void testTenantRedeployment() throws Exception {
        MockDeployer deployer = new MockDeployer();
        Tenant tenant = tenants.getTenant(tenant1);
        ApplicationId id = ApplicationId.from(tenant1, ApplicationName.defaultName(), InstanceName.defaultName());
        tenant.getApplicationRepo().createPutApplicationTransaction(id, 3).commit();
        applicationRepository.redeployAllApplications(deployer);
        assertThat(deployer.lastDeployed, Is.is(id));
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
