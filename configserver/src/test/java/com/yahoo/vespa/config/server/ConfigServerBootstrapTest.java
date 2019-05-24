// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.core.SystemTimer;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static com.yahoo.vespa.config.server.ConfigServerBootstrap.Mode.BOOTSTRAP_IN_SEPARATE_THREAD;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.Mode.INITIALIZE_ONLY;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.VipStatusMode.VIP_STATUS_FILE;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.VipStatusMode.VIP_STATUS_PROGRAMMATICALLY;
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
        InMemoryProvisioner provisioner = new InMemoryProvisioner(true, "host0", "host1", "host3", "host4");
        DeployTester tester = new DeployTester(configserverConfig, provisioner);
        tester.deployApp("src/test/apps/hosted/");

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        RpcServer rpcServer = createRpcServer(configserverConfig);
        // Take a host away so that there are too few for the application, to verify we can still bootstrap
        provisioner.allocations().values().iterator().next().remove(0);
        StateMonitor stateMonitor = new StateMonitor();
        VipStatus vipStatus = createVipStatus(stateMonitor);
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer,
                                                                    versionState, stateMonitor,
                                                                    vipStatus, INITIALIZE_ONLY, VIP_STATUS_PROGRAMMATICALLY);
        assertFalse(vipStatus.isInRotation());
        bootstrap.start();
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(vipStatus::isInRotation, "failed waiting for server to be in rotation");

        bootstrap.deconstruct();
        assertEquals(StateMonitor.Status.down, bootstrap.status());
        assertFalse(rpcServer.isRunning());
        assertFalse(vipStatus.isInRotation());
    }

    // Just tests setup, the actual response of accessing /status.html depends on the status
    // file existing or not, which cannot be tested here
    @Test
    public void testBootstrapWithVipStatusFile() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        InMemoryProvisioner provisioner = new InMemoryProvisioner(true, "host0", "host1", "host3", "host4");
        DeployTester tester = new DeployTester(configserverConfig, provisioner);
        tester.deployApp("src/test/apps/hosted/");

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        RpcServer rpcServer = createRpcServer(configserverConfig);
        StateMonitor stateMonitor = new StateMonitor();
        VipStatus vipStatus = createVipStatus(stateMonitor);
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer,
                                                                    versionState, stateMonitor,
                                                                    vipStatus, INITIALIZE_ONLY, VIP_STATUS_FILE);
        assertTrue(vipStatus.isInRotation()); // default is in rotation when using status file

        bootstrap.start();
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(vipStatus::isInRotation, "failed waiting for server to be in rotation");
        bootstrap.deconstruct();
    }

    @Test
    public void testBootstrapWhenRedeploymentFails() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        DeployTester tester = new DeployTester(configserverConfig);
        tester.deployApp("src/test/apps/hosted/");

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        // Manipulate application package so that it will fail deployment when config server starts
        java.nio.file.Files.delete(Paths.get(configserverConfig.configServerDBDir())
                                           .resolve("tenants/")
                                           .resolve(tester.tenant().getName().value())
                                           .resolve("sessions/2/services.xml"));

        RpcServer rpcServer = createRpcServer(configserverConfig);
        StateMonitor stateMonitor = new StateMonitor();
        VipStatus vipStatus = createVipStatus(stateMonitor);
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer, versionState,
                                                                    stateMonitor,
                                                                    vipStatus, INITIALIZE_ONLY, VIP_STATUS_PROGRAMMATICALLY);
        assertFalse(vipStatus.isInRotation());
        // Call method directly, to be sure that it is finished redeploying all applications and we can check status
        bootstrap.start();
        // App is invalid, bootstrapping was unsuccessful. Status should be 'initializing',
        // rpc server should not be running and it should be out of rotation
        assertEquals(StateMonitor.Status.initializing, stateMonitor.status());
        assertFalse(rpcServer.isRunning());
        assertFalse(vipStatus.isInRotation());

        bootstrap.deconstruct();
    }

    // Tests that we do not try to create the config model version stored in zookeeper when not on hosted vespa, since
    // we are then only able to create the latest version
    @Test
    public void testBootstrapNonHostedOneConfigModel() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfigNonHosted(temporaryFolder);
        String vespaVersion = "1.2.3";
        List<ModelFactory> modelFactories = Collections.singletonList(DeployTester.createModelFactory(Version.fromString(vespaVersion)));
        List<Host> hosts = createHosts(vespaVersion);
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);
        Curator curator = new MockCurator();
        DeployTester tester = new DeployTester(modelFactories, configserverConfig,
                                               Clock.systemUTC(), new Zone(Environment.dev, RegionName.defaultName()),
                                               provisioner, curator);
        tester.deployApp("src/test/apps/app/", vespaVersion, Instant.now());
        ApplicationId applicationId = tester.applicationId();

        File versionFile = temporaryFolder.newFile();
        VersionState versionState = new VersionState(versionFile);
        assertTrue(versionState.isUpgraded());

        // Ugly hack, but I see no other way of doing it:
        // Manipulate application version in zookeeper so that it is an older version than the model we know, which is
        // the case when upgrading on non-hosted installations
        curator.set(Path.fromString("/config/v2/tenants/" + applicationId.tenant().value() + "/sessions/2/version"), Utf8.toBytes("1.2.2"));

        RpcServer rpcServer = createRpcServer(configserverConfig);
        StateMonitor stateMonitor = createStateMonitor();
        VipStatus vipStatus = createVipStatus(stateMonitor);
        ConfigServerBootstrap bootstrap = new ConfigServerBootstrap(tester.applicationRepository(), rpcServer, versionState,
                                                                    stateMonitor, vipStatus,
                                                                    BOOTSTRAP_IN_SEPARATE_THREAD, VIP_STATUS_PROGRAMMATICALLY);
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(vipStatus::isInRotation, "failed waiting for server to be in rotation");
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
        return createConfigserverConfig(temporaryFolder, true);
    }

    private static ConfigserverConfig createConfigserverConfigNonHosted(TemporaryFolder temporaryFolder) throws IOException {
        return createConfigserverConfig(temporaryFolder, false);
    }

    private static ConfigserverConfig createConfigserverConfig(TemporaryFolder temporaryFolder, boolean hosted) throws IOException {
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                                              .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath())
                                              .hostedVespa(hosted)
                                              .multitenant(hosted)
                                              .maxDurationOfBootstrap(2) /* seconds */
                                              .sleepTimeWhenRedeployingFails(0)); /* seconds */
    }

    private List<Host> createHosts(String vespaVersion) {
        return Arrays.asList(createHost("host1", vespaVersion), createHost("host2", vespaVersion), createHost("host3", vespaVersion));
    }

    private Host createHost(String hostname, String version) {
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.of(com.yahoo.component.Version.fromString(version)));
    }

    private VipStatus createVipStatus(StateMonitor stateMonitor) {
        return new VipStatus(new QrSearchersConfig.Builder().build(),
                             new ClustersStatus(),
                             stateMonitor);
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
