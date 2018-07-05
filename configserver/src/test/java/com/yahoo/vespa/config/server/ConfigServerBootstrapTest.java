// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.core.SystemTimer;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @author Harald Musum
 */
public class ConfigServerBootstrapTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testBootstrap() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        InMemoryProvisioner provisioner = new InMemoryProvisioner(true, "host0", "host1", "host3");
        DeployTester tester = new DeployTester(configserverConfig, provisioner);
        tester.deployApp("src/test/apps/hosted/", "myApp", "4.5.6", Instant.now());

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        RpcServer rpcServer = createRpcServer(configserverConfig);
        VipStatus vipStatus = new VipStatus();
        // Take a host away so that there are too few for the application, to verify we can still bootstrap
        ClusterSpec contentCluster = ClusterSpec.from(ClusterSpec.Type.content,
                                                      ClusterSpec.Id.from("music"),
                                                      ClusterSpec.Group.from(0),
                                                      new com.yahoo.component.Version(4, 5, 6),
                                                      false);
        provisioner.allocations().get(contentCluster).remove(0);
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer, versionState, createStateMonitor(), vipStatus);
        assertFalse(vipStatus.isInRotation());
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(vipStatus::isInRotation, "failed waiting for server to be in rotation");

        bootstrap.deconstruct();
        assertEquals(StateMonitor.Status.down, bootstrap.status());
        assertFalse(rpcServer.isRunning());
        assertFalse(vipStatus.isInRotation());
    }

    @Test
    public void testBootstrapWhenRedeploymentFails() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        DeployTester tester = new DeployTester(configserverConfig);
        tester.deployApp("src/test/apps/hosted/", "myApp", "4.5.6", Instant.now());

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        // Manipulate application package so that it will fail deployment when config server starts
        java.nio.file.Files.delete(Paths.get(configserverConfig.configServerDBDir())
                                           .resolve("tenants/")
                                           .resolve(tester.tenant().getName().value())
                                           .resolve("sessions/2/services.xml"));

        RpcServer rpcServer = createRpcServer(configserverConfig);
        VipStatus vipStatus = new VipStatus();
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer, versionState,
                                                                    createStateMonitor(), vipStatus,
                                                                    ConfigServerBootstrap.MainThread.DO_NOT_START,
                                                                    ConfigServerBootstrap.RedeployingApplicationsFails.CONTINUE);
        assertFalse(vipStatus.isInRotation());
        // Call method directly, to be sure that it is finished redeploying all applications and we can check status
        bootstrap.run();
        // App is invalid, bootstrapping was unsuccessful. Status should be 'initializing',
        // rpc server should not be running and it should be out of rotation
        assertEquals(StateMonitor.Status.initializing, bootstrap.status());
        assertFalse(rpcServer.isRunning());
        assertFalse(vipStatus.isInRotation());
    }

    private void waitUntil(BooleanSupplier booleanSupplier, String messageIfWaitingFails) throws InterruptedException {
        Duration timeout = Duration.ofSeconds(60);
        Instant endTime = Instant.now().plus(timeout);
        while (Instant.now().isBefore(endTime)) {
            if (booleanSupplier.getAsBoolean())
                return;
            Thread.sleep(10);
        }
        throw new RuntimeException(messageIfWaitingFails);
    }

    private MockRpc createRpcServer(ConfigserverConfig configserverConfig) throws IOException {
        return new MockRpc(configserverConfig.rpcport(), temporaryFolder.newFolder());
    }

    private StateMonitor createStateMonitor() {
        return new StateMonitor(new HealthMonitorConfig(new HealthMonitorConfig.Builder().initialStatus("initializing")),
                                new SystemTimer());
    }

    private static ConfigserverConfig createConfigserverConfig(TemporaryFolder temporaryFolder) throws IOException {
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                                              .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath())
                                              .hostedVespa(true)
                                              .multitenant(true)
                                              .maxDurationOfBootstrap(1) /* seconds */);
    }

    public static class MockRpc extends com.yahoo.vespa.config.server.rpc.MockRpc {

        volatile boolean isRunning = false;

        MockRpc(int port, File tempDir) {
            super(port, tempDir);
        }

        @Override
        public void run() {
            isRunning = true;
        }

        @Override
        public void stop() {
            isRunning = false;
        }

        @Override
        public boolean isRunning() {
            return isRunning;
        }
    }

}
