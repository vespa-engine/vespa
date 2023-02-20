// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static com.yahoo.vespa.config.server.ConfigServerBootstrap.RedeployingApplicationsFails.CONTINUE;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.VipStatusMode;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.VipStatusMode.VIP_STATUS_FILE;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.VipStatusMode.VIP_STATUS_PROGRAMMATICALLY;
import static com.yahoo.vespa.config.server.deploy.DeployTester.createHostedModelFactory;
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
        InMemoryProvisioner provisioner = new InMemoryProvisioner(9, false);
        DeployTester tester = new DeployTester.Builder(temporaryFolder).modelFactory(createHostedModelFactory())
                                                                       .configserverConfig(configserverConfig)
                                                                       .hostProvisioner(provisioner).build();
        tester.deployApp("src/test/apps/hosted/");

        RpcServer rpcServer = createRpcServer(configserverConfig);
        // Take a host away so that there are too few for the application, to verify we can still bootstrap
        provisioner.allocations().values().iterator().next().remove(0);
        Bootstrapper bootstrap = createBootstrapper(tester, rpcServer, VIP_STATUS_PROGRAMMATICALLY);
        assertEquals(List.of("ApplicationPackageMaintainer", "TenantsMaintainer"),
                     bootstrap.configServerMaintenance().maintainers().stream()
                              .map(Maintainer::name)
                              .sorted().toList());
        assertFalse(bootstrap.vipStatus().isInRotation());

        bootstrap.doStart();
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        assertTrue(rpcServer.isServingConfigRequests());
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(() -> bootstrap.vipStatus().isInRotation(), "failed waiting for server to be in rotation");
        assertEquals(List.of("ApplicationPackageMaintainer", "FileDistributionMaintainer", "ReindexingMaintainer", "SessionsMaintainer", "TenantsMaintainer"),
                     bootstrap.configServerMaintenance().maintainers().stream()
                              .map(Maintainer::name)
                              .sorted().toList());

        bootstrap.deconstruct();
        assertEquals(StateMonitor.Status.down, bootstrap.status());
        assertFalse(rpcServer.isRunning());
        assertTrue(rpcServer.isServingConfigRequests());
        assertFalse(bootstrap.vipStatus().isInRotation());
    }

    // Just tests setup, the actual response of accessing /status.html depends on the status
    // file existing or not, which cannot be tested here
    @Test
    public void testBootstrapWithVipStatusFile() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        InMemoryProvisioner provisioner = new InMemoryProvisioner(9, false);
        DeployTester tester = new DeployTester.Builder(temporaryFolder).modelFactory(createHostedModelFactory())
                .configserverConfig(configserverConfig).hostProvisioner(provisioner).build();
        tester.deployApp("src/test/apps/hosted/");

        RpcServer rpcServer = createRpcServer(configserverConfig);
        Bootstrapper bootstrap = createBootstrapper(tester, rpcServer, VIP_STATUS_FILE);
        assertTrue(bootstrap.vipStatus().isInRotation()); // default is in rotation when using status file

        bootstrap.doStart();
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        assertTrue(rpcServer.isServingConfigRequests());
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(() -> bootstrap.vipStatus().isInRotation(), "failed waiting for server to be in rotation");
        bootstrap.deconstruct();
    }

    @Test
    public void testBootstrapWhenRedeploymentFails() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfig(temporaryFolder);
        DeployTester tester = new DeployTester.Builder(temporaryFolder).modelFactory(createHostedModelFactory())
                .configserverConfig(configserverConfig).build();
        tester.deployApp("src/test/apps/hosted/");

        // Manipulate application package so that it will fail deployment when config server starts
        java.nio.file.Files.delete(Paths.get(configserverConfig.configServerDBDir())
                                           .resolve("tenants/")
                                           .resolve(tester.tenant().getName().value())
                                           .resolve("sessions/2/services.xml"));

        RpcServer rpcServer = createRpcServer(configserverConfig);
        Bootstrapper bootstrap = createBootstrapper(tester, rpcServer, VIP_STATUS_PROGRAMMATICALLY);
        assertFalse(bootstrap.vipStatus().isInRotation());
        // Call method directly, to be sure that it is finished redeploying all applications and we can check status
        bootstrap.doStart();
        // App is invalid, bootstrapping was unsuccessful. Status should be 'initializing',
        // rpc server should not be running and it should be out of rotation
        assertEquals(StateMonitor.Status.initializing, bootstrap.status());
        assertTrue(rpcServer.isRunning());
        assertFalse(rpcServer.isServingConfigRequests());
        assertFalse(bootstrap.vipStatus().isInRotation());

        bootstrap.deconstruct();
    }

    // Tests that we do not try to create the config model version stored in zookeeper when not on hosted vespa, since
    // we are then only able to create the latest version
    @Test
    public void testBootstrapNonHostedOneConfigModel() throws Exception {
        ConfigserverConfig configserverConfig = createConfigserverConfigNonHosted(temporaryFolder);
        String vespaVersion = "1.2.3";
        List<Host> hosts = createHosts(vespaVersion);
        Curator curator = new MockCurator();
        DeployTester tester = new DeployTester.Builder(temporaryFolder)
                .modelFactory(DeployTester.createModelFactory(Version.fromString(vespaVersion)))
                .hostProvisioner(new InMemoryProvisioner(new Hosts(hosts), true, false))
                .configserverConfig(configserverConfig)
                .zone(new Zone(Environment.dev, RegionName.defaultName()))
                .curator(curator)
                .build();
        tester.deployApp("src/test/apps/app/", vespaVersion);
        ApplicationId applicationId = tester.applicationId();

        // Ugly hack, but I see no other way of doing it:
        // Manipulate application version in zookeeper so that it is an older version than the model we know, which is
        // the case when upgrading on non-hosted installations
        curator.set(Path.fromString("/config/v2/tenants/" + applicationId.tenant().value() + "/sessions/2/version"), Utf8.toBytes("1.2.2"));

        RpcServer rpcServer = createRpcServer(configserverConfig);
        Bootstrapper bootstrap = createBootstrapper(tester, rpcServer, VIP_STATUS_PROGRAMMATICALLY);
        bootstrap.doStart();
        waitUntil(rpcServer::isRunning, "failed waiting for Rpc server running");
        assertTrue(rpcServer.isServingConfigRequests());
        waitUntil(() -> bootstrap.status() == StateMonitor.Status.up, "failed waiting for status 'up'");
        waitUntil(() -> bootstrap.vipStatus().isInRotation(), "failed waiting for server to be in rotation");
    }

    private Bootstrapper createBootstrapper(DeployTester tester, RpcServer rpcServer, VipStatusMode vipStatusMode) throws IOException {
        VersionState versionState = createVersionState(tester.curator());
        assertTrue(versionState.isUpgraded());

        StateMonitor stateMonitor = StateMonitor.createForTesting();
        VipStatus vipStatus = createVipStatus(stateMonitor);
        return new Bootstrapper(tester.applicationRepository(),
                                rpcServer,
                                versionState,
                                stateMonitor,
                                vipStatus,
                                vipStatusMode,
                                new FileDirectory(tester.applicationRepository().configserverConfig()));
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

    private MockRpcServer createRpcServer(ConfigserverConfig configserverConfig) throws IOException {
        return new MockRpcServer(configserverConfig.rpcport(), temporaryFolder.newFolder());
    }

    private static ConfigserverConfig createConfigserverConfig(TemporaryFolder temporaryFolder) throws IOException {
        return createConfigserverConfig(temporaryFolder, true);
    }

    private static ConfigserverConfig createConfigserverConfigNonHosted(TemporaryFolder temporaryFolder) throws IOException {
        return createConfigserverConfig(temporaryFolder, false);
    }

    private static ConfigserverConfig createConfigserverConfig(TemporaryFolder temporaryFolder, boolean hosted) throws IOException {
        var servers = List.of(new ConfigserverConfig.Zookeeperserver.Builder().hostname("foo").port(1),
                              new ConfigserverConfig.Zookeeperserver.Builder().hostname("bar").port(1),
                              new ConfigserverConfig.Zookeeperserver.Builder().hostname("baz").port(1));
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                                              .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath())
                                              .fileReferencesDir(temporaryFolder.newFolder("filedistribution").getAbsolutePath())
                                              .hostedVespa(hosted)
                                              .multitenant(hosted)
                                              .maxDurationOfBootstrap(0) /* seconds, 0 => it will not retry deployment if bootstrap fails */
                                              .sleepTimeWhenRedeployingFails(0) /* seconds */
                                              .zookeeperserver(servers));
    }

    private List<Host> createHosts(String vespaVersion) {
        return Arrays.asList(createHost("host1", vespaVersion), createHost("host2", vespaVersion), createHost("host3", vespaVersion));
    }

    private Host createHost(String hostname, String version) {
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.of(com.yahoo.component.Version.fromString(version)));
    }

    private VipStatus createVipStatus(StateMonitor stateMonitor) {
        return new VipStatus(new QrSearchersConfig.Builder().build(),
                             new VipStatusConfig.Builder().build(),
                             new ClustersStatus(),
                             stateMonitor,
                             new NullMetric());
    }

    private VersionState createVersionState(Curator curator) throws IOException {
        return new VersionState(temporaryFolder.newFile(), curator);
    }

    public static class MockRpcServer extends com.yahoo.vespa.config.server.rpc.MockRpcServer {

        volatile boolean isRunning = false;

        MockRpcServer(int port, File tempDir) {
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

    private static class Bootstrapper extends ConfigServerBootstrap {

        public Bootstrapper(ApplicationRepository applicationRepository,
                            RpcServer server,
                            VersionState versionState,
                            StateMonitor stateMonitor,
                            VipStatus vipStatus,
                            VipStatusMode vipStatusMode,
                            FileDirectory fileDirectory) {
            super(applicationRepository, server, versionState, stateMonitor, vipStatus, CONTINUE, vipStatusMode, fileDirectory);
        }

        @Override
        public void start() {
            // Do nothing, avoids bootstrapping apps in constructor, use doBootstrap() below to really bootstrap apps
        }

        public void doStart() {
            super.start();
        }

    }

}
